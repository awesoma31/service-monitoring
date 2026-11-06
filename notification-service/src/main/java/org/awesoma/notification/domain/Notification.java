package org.awesoma.notification.domain;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Notification {

    private Long id;

    /** An incident of monitor-service; another database, so an id rather than an association. */

    private Long incidentId;

    /** Set instead of the incident on a notification about an uploaded report. */
    private UUID reportId;

    private Channel channel;

    private OffsetDateTime sentAt;

    private IncidentKind incidentKind;

    private String subject;

    private String message;

    private NotificationStatus status = NotificationStatus.PENDING;

    private int attempts;

    private OffsetDateTime nextAttemptAt = OffsetDateTime.now();

    private String lastError;

    private UUID deliveryClaimToken;

    private OffsetDateTime deliveryClaimedUntil;

    public Notification(
            Long incidentId,
            Channel channel,
            IncidentKind incidentKind,
            String subject,
            String message) {
        this.incidentId = incidentId;
        this.channel = channel;
        this.incidentKind = incidentKind;
        this.subject = subject;
        this.message = message;
    }

    public static Notification forReport(UUID reportId, Channel channel, String subject, String message) {
        Notification notification = new Notification(null, channel, null, subject, message);
        notification.reportId = reportId;
        return notification;
    }

    public void claim(UUID token, OffsetDateTime until) {
        this.deliveryClaimToken = token;
        this.deliveryClaimedUntil = until;
    }

    public boolean isClaimedBy(UUID token) {
        return token != null && token.equals(deliveryClaimToken);
    }

    /** The database rejects a SENT notification without a timestamp, so both move together. */
    public void markSent(UUID token, OffsetDateTime at) {
        requireClaim(token);
        this.status = NotificationStatus.SENT;
        this.sentAt = at;
        this.attempts++;
        this.lastError = null;
        clearClaim();
    }

    public void markAttemptFailed(
            UUID token,
            OffsetDateTime at,
            int maxAttempts,
            Duration retryDelay,
            String error) {
        requireClaim(token);
        this.attempts++;
        this.sentAt = null;
        this.lastError = truncate(error, 1000);
        if (attempts >= maxAttempts) {
            this.status = NotificationStatus.FAILED;
        } else {
            this.status = NotificationStatus.PENDING;
            this.nextAttemptAt = at.plus(retryDelay.multipliedBy(attempts));
        }
        clearClaim();
    }

    private void requireClaim(UUID token) {
        if (!isClaimedBy(token)) {
            throw new IllegalStateException("Notification delivery claim is no longer active");
        }
    }

    private void clearClaim() {
        this.deliveryClaimToken = null;
        this.deliveryClaimedUntil = null;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
