package org.awesoma.monitoring.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.awesoma.monitoring.domain.enums.CheckResultType;

@Entity
@Table(name = "check_results")
@Getter
@Setter
@NoArgsConstructor
public class CheckResult extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "monitor_id", nullable = false)
    private Monitor monitor;

    @NotNull
    @Column(name = "checked_at", nullable = false)
    private OffsetDateTime checkedAt;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CheckResultType result;

    @Min(0)
    @Column(name = "response_ms")
    private Integer responseMs;

    @Min(100)
    @Max(599)
    @Column(name = "http_status")
    private Integer httpStatus;

    @Size(max = 1000)
    @Column(name = "error_message", length = 1000)
    private String errorMessage;
}
