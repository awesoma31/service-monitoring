package org.awesoma.report.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Limits of stored files. Spring reads {@code MB} as 2^20 bytes, so {@code 10MB} is
 * 10 485 760 bytes.
 */
@Validated
@ConfigurationProperties("report")
public record ReportProperties(
        @NotNull @DefaultValue("10MB") DataSize maxFileSize,
        @NotNull @DefaultValue("PT10M") Duration uploadTimeout,
        @DefaultValue("50") int maxPageSize) {
}
