package org.awesoma.report.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Limits of stored and generated files. Spring reads {@code MB} as 2^20 bytes, so
 * {@code 10MB} is 10 485 760 bytes.
 */
@Validated
@ConfigurationProperties("report")
public record ReportProperties(
        @NotNull @DefaultValue("10MB") DataSize maxFileSize,
        @NotNull @DefaultValue("PT10M") Duration uploadTimeout,
        @DefaultValue("50") int maxPageSize,
        @Valid @NotNull @DefaultValue Generation generation,
        @NotNull @DefaultValue("PT24H") Duration idempotencyTombstoneTtl,
        @NotNull @DefaultValue("PT15M") Duration reconciliationGrace) {

    public record Generation(
            @Min(1) @DefaultValue("31") int maxPeriodDays,
            @Min(1) @DefaultValue("100") int maxMonitors,
            @Min(1) @DefaultValue("100000") long maxSourceRows,
            @Min(1) @Max(50) @DefaultValue("50") int pageSize,
            @NotNull @DefaultValue("PT5M") Duration timeout,
            @Min(1) @DefaultValue("2") int concurrency,
            @Min(1) @DefaultValue("3") int maxAttempts,
            @NotNull @DefaultValue("PT30S") Duration retryDelay,
            @NotNull @DefaultValue("PT1M") Duration leaseDuration) {
    }
}
