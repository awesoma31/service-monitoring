package org.awesoma.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** An incident of monitor-service; another database, so an id rather than an association. */
    @NotNull
    @Column(name = "incident_id", nullable = false)
    private Long incidentId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_id", nullable = false)
    private Channel channel;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "incident_kind", nullable = false, length = 20)
    private IncidentKind incidentKind;

    @NotNull
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    private String subject;

    @NotNull
    @Size(max = 2000)
    @Column(nullable = false, length = 2000)
    private String message;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Min(0)
    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private OffsetDateTime nextAttemptAt = OffsetDateTime.now();

    @Size(max = 1000)
    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "delivery_claim_token", columnDefinition = "uuid")
    private UUID deliveryClaimToken;

    @Column(name = "delivery_claimed_until")
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
            java.time.Duration retryDelay,
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
