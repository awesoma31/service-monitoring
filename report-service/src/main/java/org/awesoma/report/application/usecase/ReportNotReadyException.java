package org.awesoma.report.application.usecase;

import org.awesoma.report.domain.ReportStatus;

/** The content can be downloaded only once the report is READY. */
public class ReportNotReadyException extends RuntimeException {
    public ReportNotReadyException(ReportStatus status) {
        super("Report is " + status + ", its content is available only when it is READY");
    }
}
