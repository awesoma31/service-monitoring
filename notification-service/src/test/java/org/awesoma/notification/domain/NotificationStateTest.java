package org.awesoma.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationStateTest {

    @Test
    void startsPendingWithNoAttempts() {
        Notification notification = notification();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getAttempts()).isZero();
        assertThat(notification.getSentAt()).isNull();
        assertThat(notification.getNextAttemptAt()).isNotNull();
    }

    @Test
    void markSentRecordsTheTimeAndCountsTheAttempt() {
        Notification notification = notification();
        UUID token = UUID.randomUUID();
        OffsetDateTime at = OffsetDateTime.now();
        notification.claim(token, at.plusMinutes(1));

        notification.markSent(token, at);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getSentAt()).isEqualTo(at);
        assertThat(notification.getAttempts()).isEqualTo(1);
        assertThat(notification.getDeliveryClaimToken()).isNull();
        assertThat(notification.getDeliveryClaimedUntil()).isNull();
    }

    @Test
    void aFailedAttemptIsRetriedWithBackoff() {
        Notification notification = notification();
        UUID token = UUID.randomUUID();
        OffsetDateTime at = OffsetDateTime.now();
        notification.claim(token, at.plusMinutes(1));

        notification.markAttemptFailed(token, at, 3, Duration.ofSeconds(10), "SMTP unavailable");

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getAttempts()).isEqualTo(1);
        assertThat(notification.getNextAttemptAt()).isEqualTo(at.plusSeconds(10));
        assertThat(notification.getLastError()).isEqualTo("SMTP unavailable");
    }

    @Test
    void theLastFailedAttemptBecomesTerminal() {
        Notification notification = notification();
        OffsetDateTime at = OffsetDateTime.now();

        for (int attempt = 0; attempt < 2; attempt++) {
            UUID token = UUID.randomUUID();
            notification.claim(token, at.plusMinutes(1));
            notification.markAttemptFailed(token, at, 2, Duration.ZERO, "failed");
        }

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getAttempts()).isEqualTo(2);
        assertThat(notification.getSentAt()).isNull();
    }

    @Test
    void aStaleWorkerCannotCompleteAnotherWorkersClaim() {
        Notification notification = notification();
        UUID activeToken = UUID.randomUUID();
        notification.claim(activeToken, OffsetDateTime.now().plusMinutes(1));

        assertThatThrownBy(() -> notification.markSent(UUID.randomUUID(), OffsetDateTime.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("claim");
    }

    private Notification notification() {
        return new Notification(
                1L,
                new Channel(),
                IncidentKind.OPENED,
                "Incident opened",
                "The service is unavailable");
    }
}
