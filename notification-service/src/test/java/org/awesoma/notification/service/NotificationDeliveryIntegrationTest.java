package org.awesoma.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import org.awesoma.notification.config.DeliveryProperties;
import org.awesoma.notification.delivery.DeliveryException;
import org.awesoma.notification.delivery.EmailNotificationSender;
import org.awesoma.notification.delivery.TelegramNotificationSender;
import org.awesoma.notification.delivery.WebhookNotificationSender;
import org.awesoma.notification.domain.Channel;
import org.awesoma.notification.domain.ChannelType;
import org.awesoma.notification.domain.IncidentKind;
import org.awesoma.notification.domain.Notification;
import org.awesoma.notification.domain.NotificationStatus;
import org.awesoma.notification.repository.ChannelRepository;
import org.awesoma.notification.repository.NotificationRepository;
import org.awesoma.notification.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@ExtendWith(OutputCaptureExtension.class)
class NotificationDeliveryIntegrationTest extends AbstractIntegrationTest {

    @Autowired private NotificationDeliveryService delivery;
    @MockitoSpyBean private NotificationRepository notifications;
    @Autowired private ChannelRepository channels;
    @Autowired private DeliveryProperties properties;

    @MockitoBean private EmailNotificationSender emailSender;
    @MockitoBean private TelegramNotificationSender telegramSender;
    @MockitoBean private WebhookNotificationSender webhookSender;

    @BeforeEach
    void configureSenders() {
        notifications.deleteAllInBatch();
        channels.deleteAllInBatch();
        when(emailSender.supportedType()).thenReturn(ChannelType.EMAIL);
        when(telegramSender.supportedType()).thenReturn(ChannelType.TELEGRAM);
        when(webhookSender.supportedType()).thenReturn(ChannelType.WEBHOOK);
        when(emailSender.send(any())).thenReturn(Mono.empty());
    }

    @Test
    void successfulDeliveryIsPersisted() {
        Notification notification = pendingEmail();

        assertThat(delivery.dispatchOnce().block()).isEqualTo(1);

        Notification delivered = notifications.findById(notification.getId()).orElseThrow();
        assertThat(delivered.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(delivered.getSentAt()).isNotNull();
        assertThat(delivered.getAttempts()).isEqualTo(1);
        assertThat(delivered.getDeliveryClaimToken()).isNull();
        verify(emailSender).send(any());
    }

    @Test
    void concurrentDispatchersDoNotSendTheSameClaimTwice() {
        pendingEmail();
        when(emailSender.send(any())).thenReturn(Mono.delay(Duration.ofMillis(100)).then());

        Mono.zip(
                        delivery.dispatchOnce().subscribeOn(Schedulers.boundedElastic()),
                        delivery.dispatchOnce().subscribeOn(Schedulers.boundedElastic()))
                .block();

        verify(emailSender, times(1)).send(any());
        assertThat(notifications.findAll())
                .singleElement()
                .extracting(Notification::getStatus)
                .isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void failedDeliveryIsPersistedAndScheduledForRetry() {
        Notification notification = pendingEmail();
        when(emailSender.send(any())).thenReturn(Mono.error(new DeliveryException("SMTP unavailable")));

        assertThat(delivery.dispatchOnce().block()).isEqualTo(1);

        Notification failedAttempt = notifications.findById(notification.getId()).orElseThrow();
        assertThat(failedAttempt.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(failedAttempt.getAttempts()).isEqualTo(1);
        assertThat(failedAttempt.getLastError()).isEqualTo("SMTP unavailable");
        assertThat(failedAttempt.getNextAttemptAt()).isAfter(OffsetDateTime.now());
        assertThat(failedAttempt.getDeliveryClaimToken()).isNull();
    }

    @Test
    void aFailedWriteAfterASuccessfulSendIsRetriedInsteadOfSendingAgain() {
        Notification notification = pendingEmail();
        // The repository is a proxy, so the real call goes through the spy's default answer.
        Answer<?> real = mockingDetails(notifications).getMockCreationSettings().getDefaultAnswer();
        AtomicBoolean firstWrite = new AtomicBoolean(true);
        doAnswer(call -> {
                    if (firstWrite.getAndSet(false)) {
                        throw new TransientDataAccessResourceException("database blinked");
                    }
                    return real.answer(call);
                })
                .when(notifications)
                .findForUpdateById(notification.getId());

        assertThat(delivery.dispatchOnce().block()).isEqualTo(1);

        Notification delivered = notifications.findById(notification.getId()).orElseThrow();
        assertThat(delivered.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(delivered.getAttempts()).isEqualTo(1);
        assertThat(delivered.getLastError()).isNull();
        verify(emailSender, times(1)).send(any());
    }

    @Test
    void exhaustedAttemptsAreLoggedAsAnErrorWithoutTransportCredentials(CapturedOutput output) {
        Notification notification = pendingEmail();
        notification.setAttempts(properties.maxAttempts() - 1);
        notifications.saveAndFlush(notification);
        when(emailSender.send(any())).thenReturn(Mono.error(new DeliveryException(
                "Call failed: bot123456:private-bot-token/sendMessage",
                new IllegalStateException("nested-private-credential"))));

        assertThat(delivery.dispatchOnce().block()).isEqualTo(1);

        assertThat(notifications.findById(notification.getId()).orElseThrow().getStatus())
                .isEqualTo(NotificationStatus.FAILED);
        assertThat(output.getOut())
                .contains("ERROR", "Notification delivery attempts exhausted: notification_id=" + notification.getId(),
                        "channel_type=EMAIL", "attempt=" + properties.maxAttempts(), "bot<redacted>")
                .doesNotContain("private-bot-token", "nested-private-credential");
    }

    private Notification pendingEmail() {
        Channel channel = new Channel();
        channel.setProjectId(System.nanoTime());
        channel.setType(ChannelType.EMAIL);
        channel.setTarget("owner@example.com");
        channel = channels.saveAndFlush(channel);

        Notification notification = new Notification(
                System.nanoTime(),
                channel,
                IncidentKind.OPENED,
                "Incident opened",
                "The service is unavailable");
        notification.setNextAttemptAt(OffsetDateTime.now().minusSeconds(1));
        return notifications.saveAndFlush(notification);
    }
}
