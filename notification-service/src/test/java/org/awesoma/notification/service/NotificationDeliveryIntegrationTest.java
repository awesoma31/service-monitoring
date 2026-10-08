package org.awesoma.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class NotificationDeliveryIntegrationTest extends AbstractIntegrationTest {

    @Autowired private NotificationDeliveryService delivery;
    @Autowired private NotificationRepository notifications;
    @Autowired private ChannelRepository channels;

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
