package org.awesoma.report.application.usecase;

import org.awesoma.report.application.port.StoredObject;
import org.awesoma.report.domain.Report;

/** A ready report and its open content stream, which the caller must close. */
public record ReportDownload(Report report, StoredObject content) {
}
