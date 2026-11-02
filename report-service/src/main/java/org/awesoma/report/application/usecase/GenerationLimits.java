package org.awesoma.report.application.usecase;

import java.time.Duration;

/**
 * Limits of report generation. Those known when a job is requested are checked then; those
 * only a worker can see end the job as FAILED with LIMIT_EXCEEDED, never with a cut report.
 *
 * @param maxPeriod       longest period a report may cover
 * @param maxMonitors     most monitors in one report, "all monitors" included
 * @param maxSourceRows   most checks and incidents read for one attempt, together
 * @param pageSize        rows asked of a source per request
 * @param attemptTimeout  how long one attempt may take
 * @param maxAttempts     attempts for temporary failures, the first included
 * @param retryDelay      delay before the second attempt; it doubles after each further one
 * @param leaseDuration   how long a lease lasts unless renewed
 */
public record GenerationLimits(
        Duration maxPeriod,
        int maxMonitors,
        long maxSourceRows,
        int pageSize,
        Duration attemptTimeout,
        int maxAttempts,
        Duration retryDelay,
        Duration leaseDuration) {
}
