package org.awesoma.report.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * The canonical content type follows from the allowed extension and the checked content,
 * never from what a client declared.
 */
public enum ReportFormat {
    CSV("csv", "text/csv; charset=UTF-8"),
    PDF("pdf", "application/pdf");

    private final String extension;
    private final String contentType;

    ReportFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    public static Optional<ReportFormat> fromExtension(String extension) {
        if (extension == null) return Optional.empty();
        String normalized = extension.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(format -> format.extension.equals(normalized)).findFirst();
    }
}
