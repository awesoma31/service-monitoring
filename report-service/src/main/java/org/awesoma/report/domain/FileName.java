package org.awesoma.report.domain;

import java.util.Locale;

/**
 * The name a user gave the file, kept only for display and download. It is cut off from any
 * path and never becomes part of an object key.
 */
public record FileName(String value) {
    public static final int MAX_LENGTH = 255;

    public FileName {
        if (value == null || value.isBlank()) {
            throw new InvalidReportRequestException("file", "File name is required");
        }
        if (value.length() > MAX_LENGTH) {
            throw new InvalidReportRequestException("file", "File name is longer than " + MAX_LENGTH + " characters");
        }
        if (value.chars().anyMatch(FileName::unsafe)) {
            throw new InvalidReportRequestException("file", "File name contains control or path characters");
        }
        if (!value.equals(value.strip()) || value.chars().allMatch(c -> c == '.')) {
            throw new InvalidReportRequestException("file", "File name is not a usable name");
        }
    }

    /** Takes the last segment of a client-supplied name, which browsers may send with a path. */
    public static FileName fromClient(String raw) {
        if (raw == null) return new FileName(null);
        int separator = Math.max(raw.lastIndexOf('/'), raw.lastIndexOf('\\'));
        return new FileName(raw.substring(separator + 1));
    }

    public String extension() {
        int dot = value.lastIndexOf('.');
        return dot <= 0 ? "" : value.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean unsafe(int c) {
        return Character.isISOControl(c) || c == '/' || c == '\\'
                || Character.getType(c) == Character.FORMAT
                || Character.getType(c) == Character.LINE_SEPARATOR
                || Character.getType(c) == Character.PARAGRAPH_SEPARATOR;
    }
}
