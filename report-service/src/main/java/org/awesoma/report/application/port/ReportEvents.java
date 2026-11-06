package org.awesoma.report.application.port;

import org.awesoma.report.domain.Report;

/** What other services are told about reports. Called inside the transaction of the change. */
public interface ReportEvents {
    void uploaded(Report report);
}
