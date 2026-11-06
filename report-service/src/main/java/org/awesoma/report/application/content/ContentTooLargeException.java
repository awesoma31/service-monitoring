package org.awesoma.report.application.content;

/** The content went past the configured limit; nothing beyond the limit was read. */
public class ContentTooLargeException extends ContentRejectedException {
    private final long limit;

    public ContentTooLargeException(long limit) {
        super("File is larger than " + limit + " bytes");
        this.limit = limit;
    }

    public long limit() {
        return limit;
    }
}
