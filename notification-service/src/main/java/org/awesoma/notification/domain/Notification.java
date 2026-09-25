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
import java.time.OffsetDateTime;
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
    @Column(nullable = false, length = 20)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Min(0)
    @Column(nullable = false)
    private int attempts;

    public Notification(Long incidentId, Channel channel) {
        this.incidentId = incidentId;
        this.channel = channel;
    }

    /** The database rejects a SENT notification without a timestamp, so both move together. */
    public void markSent(OffsetDateTime at) {
        this.status = NotificationStatus.SENT;
        this.sentAt = at;
        this.attempts++;
    }

    public void markFailed() {
        this.status = NotificationStatus.FAILED;
        this.sentAt = null;
        this.attempts++;
    }
}
