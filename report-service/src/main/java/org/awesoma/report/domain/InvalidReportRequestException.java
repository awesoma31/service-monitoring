package org.awesoma.report.domain;

/** A request that breaks a rule of the domain; answered with 400 before anything is stored. */
public class InvalidReportRequestException extends RuntimeException {
    private final String field;

    public InvalidReportRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
