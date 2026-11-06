package org.awesoma.report.application.usecase;

import java.io.InputStream;

/**
 * A file to store. The name and the content type are what the client sent and are checked
 * by the use case; {@code declaredSize} is negative when the client did not state it.
 */
public record UploadReportCommand(
        long projectId,
        long userId,
        String fileName,
        String declaredContentType,
        long declaredSize,
        InputStream content) {
}
