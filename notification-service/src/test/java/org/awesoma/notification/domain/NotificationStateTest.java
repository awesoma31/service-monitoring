package org.awesoma.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class NotificationStateTest {

    @Test
    void startsPendingWithNoAttempts() {
        Notification notification = new Notification(1L, new Channel());

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getAttempts()).isZero();
        assertThat(notification.getSentAt()).isNull();
    }

    @Test
    void markSentRecordsTheTimeAndCountsTheAttempt() {
        Notification notification = new Notification(1L, new Channel());
        OffsetDateTime at = OffsetDateTime.now();

        notification.markSent(at);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getSentAt()).isEqualTo(at);
        assertThat(notification.getAttempts()).isEqualTo(1);
    }

    @Test
    void markFailedClearsTheTimestampSoTheRowStaysConsistent() {
        Notification notification = new Notification(1L, new Channel());
        notification.markSent(OffsetDateTime.now());

        notification.markFailed();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getSentAt()).isNull();
        assertThat(notification.getAttempts()).isEqualTo(2);
    }
}
