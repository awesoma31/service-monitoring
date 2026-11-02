package org.awesoma.report.domain.content;

import java.math.BigDecimal;

/**
 * Check totals of one monitor. The success ratio and the response times are {@code null}
 * when there is nothing to compute them from; the ratio is a share of checks, not uptime.
 */
public record CheckStats(
        long total,
        long success,
        long badStatus,
        long timeout,
        long connectionError,
        BigDecimal successRatioPercent,
        Long responseMsMin,
        BigDecimal responseMsAvg,
        Long responseMsMax) {
}
