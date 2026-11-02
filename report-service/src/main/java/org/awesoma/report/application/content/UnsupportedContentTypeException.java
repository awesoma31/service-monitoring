package org.awesoma.report.application.content;

/** A file format the service does not take, or a declared type that contradicts it. */
public class UnsupportedContentTypeException extends RuntimeException {
    public UnsupportedContentTypeException(String message) {
        super(message);
    }
}
