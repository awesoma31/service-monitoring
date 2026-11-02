package org.awesoma.report.application.content;

/** Empty, not UTF-8 text for a CSV, or without the PDF signature for a PDF. */
public class InvalidContentException extends ContentRejectedException {
    public InvalidContentException(String message) {
        super(message);
    }
}
