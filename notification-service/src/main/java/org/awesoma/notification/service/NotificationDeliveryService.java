package org.awesoma.notification.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.awesoma.notification.config.DeliveryProperties;
import org.awesoma.notification.delivery.DeliveryException;
import org.awesoma.notification.delivery.DeliveryTask;
import org.awesoma.notification.delivery.NotificationSender;
import org.awesoma.notification.domain.Notification;
import org.awesoma.notification.repository.NotificationRepository;
import org.awesoma.notification.support.JpaExecutor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDeliveryService {

    private static final int SENT_WRITE_RETRIES = 3;
    private static final Duration SENT_WRITE_BACKOFF = Duration.ofMillis(200);

    private final NotificationRepository notifications;
    private final JpaExecutor jpa;
    private final DeliveryProperties properties;
    private final List<NotificationSender> senders;

    public Mono<Integer> dispatchOnce() {
        return claimBatch().flatMap(tasks -> Flux.fromIterable(tasks)
                .flatMap(this::deliver, properties.concurrency())
                .then(Mono.just(tasks.size())));
    }

    private Mono<List<DeliveryTask>> claimBatch() {
        return jpa.write(() -> {
            OffsetDateTime claimedUntil = OffsetDateTime.now().plus(properties.leaseDuration());
            return notifications.findReadyForClaim(properties.batchSize()).stream()
                    .map(notification -> claim(notification, claimedUntil))
                    .toList();
        });
    }

    private DeliveryTask claim(Notification notification, OffsetDateTime claimedUntil) {
        UUID token = UUID.randomUUID();
        notification.claim(token, claimedUntil);
        return new DeliveryTask(
                notification.getId(),
                token,
                notification.getChannel().getType(),
                notification.getChannel().getTarget(),
                notification.getSubject(),
                notification.getMessage(),
                notification.getAttempts());
    }

    /**
     * A failed send and a failed write after a successful send are handled apart: counting
     * the second as a failed attempt would send the same message again on the next retry.
     */
    private Mono<Void> deliver(DeliveryTask task) {
        return Mono.defer(() -> senderFor(task).send(task))
                .then(Mono.just(true))
                .onErrorResume(error -> markFailed(task, error).thenReturn(false))
                .flatMap(sent -> sent ? recordSent(task) : Mono.empty());
    }

    /**
     * If the write still fails, the claim expires and the message is sent once more, so
     * delivery is at least once; a webhook receiver can drop repeats by notification_id.
     */
    private Mono<Void> recordSent(DeliveryTask task) {
        return markSent(task)
                .retryWhen(Retry.backoff(SENT_WRITE_RETRIES, SENT_WRITE_BACKOFF))
                .onErrorResume(error -> {
                    log.error(
                            "Notification {} was sent but could not be marked as sent",
                            task.notificationId(),
                            error);
                    return Mono.empty();
                });
    }

    private NotificationSender senderFor(DeliveryTask task) {
        return senders.stream()
                .filter(sender -> sender.supportedType() == task.channelType())
                .findFirst()
                .orElseThrow(() -> new DeliveryException(
                        "No sender supports channel type " + task.channelType()));
    }

    private Mono<Void> markSent(DeliveryTask task) {
        return jpa.write(() -> {
                    notifications.findForUpdateById(task.notificationId()).ifPresent(notification -> {
                        if (notification.isClaimedBy(task.claimToken())) {
                            notification.markSent(task.claimToken(), OffsetDateTime.now());
                        }
                    });
                    return true;
                })
                .then();
    }

    private Mono<Void> markFailed(DeliveryTask task, Throwable error) {
        String errorMessage = safeErrorMessage(error);
        return jpa.write(() -> {
                    notifications.findForUpdateById(task.notificationId()).ifPresent(notification -> {
                        if (notification.isClaimedBy(task.claimToken())) {
                            notification.markAttemptFailed(
                                    task.claimToken(),
                                    OffsetDateTime.now(),
                                    properties.maxAttempts(),
                                    properties.retryDelay(),
                                    errorMessage);
                        }
                    });
                    return true;
                })
                .doOnSuccess(ignored -> log.warn(
                        "Notification {} delivery via {} failed: {}",
                        task.notificationId(),
                        task.channelType(),
                        errorMessage))
                .then();
    }

    private static String safeErrorMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            message = error.getClass().getSimpleName();
        }
        String redacted = message.replaceAll("bot[0-9]+:[^/\\s]+", "bot<redacted>");
        return redacted.length() <= 1000 ? redacted : redacted.substring(0, 1000);
    }
}
