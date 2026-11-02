package org.awesoma.report.application.content;

/** The content went past the configured limit; nothing beyond the limit was read. */
public class ContentTooLargeException extends RuntimeException {
    private final long limit;

    public ContentTooLargeException(long limit) {
        super("Content is larger than " + limit + " bytes");
        this.limit = limit;
    }

    public long limit() {
        return limit;
    }
}
