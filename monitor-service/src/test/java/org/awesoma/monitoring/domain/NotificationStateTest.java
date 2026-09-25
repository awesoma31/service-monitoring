package org.awesoma.monitoring.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.awesoma.monitoring.domain.entity.Notification;
import org.awesoma.monitoring.domain.enums.NotificationStatus;
import org.junit.jupiter.api.Test;

class NotificationStateTest {

    @Test
    void startsPendingWithNoAttempts() {
        Notification notification = new Notification();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getSentAt()).isNull();
        assertThat(notification.getAttempts()).isZero();
    }

    @Test
    void markSentRecordsTheTimeAndCountsTheAttempt() {
        Notification notification = new Notification();
        OffsetDateTime at = OffsetDateTime.now();

        notification.markSent(at);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getSentAt()).isEqualTo(at);
        assertThat(notification.getAttempts()).isOne();
    }

    @Test
    void markFailedClearsTheTimestampSoTheRowStaysConsistent() {
        Notification notification = new Notification();
        notification.markSent(OffsetDateTime.now());

        notification.markFailed();

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getSentAt()).isNull();
        assertThat(notification.getAttempts()).isEqualTo(2);
    }
}
