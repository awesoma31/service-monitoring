package org.awesoma.report.application.content;

import java.util.Locale;
import org.awesoma.report.domain.ReportFormat;

/**
 * Checks the type a client declared for the file part. It never decides the format, which
 * comes from the extension and the content; it only refuses a type that contradicts them.
 * Missing and {@code application/octet-stream} say nothing and are accepted.
 */
public final class DeclaredContentType {

    private DeclaredContentType() {
    }

    public static void requireCompatible(String declared, ReportFormat format) {
        if (declared == null || declared.isBlank()) return;
        String[] parts = declared.split(";");
        String type = parts[0].strip().toLowerCase(Locale.ROOT);
        if (type.equals("application/octet-stream")) return;
        String expected = format == ReportFormat.CSV ? "text/csv" : "application/pdf";
        if (!type.equals(expected)) {
            throw new UnsupportedContentTypeException(
                    "Declared type " + type + " does not match a ." + format.extension() + " file");
        }
        for (int i = 1; i < parts.length; i++) {
            String[] parameter = parts[i].split("=", 2);
            if (!parameter[0].strip().equalsIgnoreCase("charset")) continue;
            String charset = parameter.length < 2 ? "" : parameter[1].strip().replace("\"", "");
            if (format != ReportFormat.CSV || !charset.equalsIgnoreCase("UTF-8")) {
                throw new UnsupportedContentTypeException("Only UTF-8 CSV files are accepted");
            }
        }
    }
}
