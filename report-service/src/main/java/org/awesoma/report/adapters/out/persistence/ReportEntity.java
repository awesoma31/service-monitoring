package org.awesoma.report.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.awesoma.report.domain.FailureCode;
import org.awesoma.report.domain.ReportFormat;
import org.awesoma.report.domain.ReportSource;
import org.awesoma.report.domain.ReportStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** The table row of a report; the rules live in the domain {@code Report}, not here. */
@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
public class ReportEntity {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private long projectId;

    @Column(name = "created_by", nullable = false)
    private long createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReportSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private ReportFormat format;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReportStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "object_key")
    private String objectKey;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(length = 64)
    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_code", length = 32)
    private FailureCode failureCode;

    @Column(name = "period_from")
    private Instant periodFrom;

    @Column(name = "period_to")
    private Instant periodTo;

    @Column(name = "all_monitors")
    private Boolean allMonitors;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "monitor_ids", columnDefinition = "bigint[]")
    private Long[] monitorIds;

    @Column(name = "requested_at")
    private Instant requestedAt;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Column(name = "lease_attempt_id")
    private UUID leaseAttemptId;

    @Column(name = "lease_token")
    private UUID leaseToken;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "bounds_observed_at")
    private Instant boundsObservedAt;

    @Column(name = "checks_upper_id")
    private Long checksUpperId;

    @Column(name = "incidents_upper_id")
    private Long incidentsUpperId;
}
