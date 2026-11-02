package org.awesoma.report.application.content;

/**
 * The content itself is unacceptable, as opposed to the storage failing to take it. Raised
 * from inside the stream while it is being transferred.
 */
public class ContentRejectedException extends RuntimeException {
    public ContentRejectedException(String message) {
        super(message);
    }
}
