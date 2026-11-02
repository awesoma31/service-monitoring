package org.awesoma.report.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/** What a generation was asked for, fixed when the job is accepted. */
public record GenerationParameters(ReportPeriod period, MonitorSelection selection, Instant requestedAt) {

    /**
     * The hash an idempotency key is bound to. It covers what changes the result (UTC period,
     * format and selection) and not the request time, so a retried request matches.
     */
    public String fingerprint(ReportFormat format) {
        String canonical = period.from() + "|" + period.to() + "|" + format + "|" + selection.canonical();
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime", error);
        }
    }
}
