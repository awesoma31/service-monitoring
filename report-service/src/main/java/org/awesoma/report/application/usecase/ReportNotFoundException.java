package org.awesoma.report.application.usecase;

/** The report or its project does not exist, or the report is being deleted. */
public class ReportNotFoundException extends RuntimeException {
    public ReportNotFoundException(String message) {
        super(message);
    }
}
