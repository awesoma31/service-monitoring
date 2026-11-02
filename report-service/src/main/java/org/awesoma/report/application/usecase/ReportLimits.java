package org.awesoma.report.application.usecase;

import java.time.Duration;

/**
 * @param maxFileSize    bytes a stored file may have, uploaded or generated
 * @param uploadTimeout  how long an upload may take before its lease expires
 * @param maxPageSize    the largest page of a list
 */
public record ReportLimits(long maxFileSize, Duration uploadTimeout, int maxPageSize) {
}
