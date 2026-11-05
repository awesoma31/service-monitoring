package org.awesoma.report.adapters.out.filesystem;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.awesoma.report.application.port.ReportWorkspace;
import org.awesoma.report.domain.ReportFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Filesystem adapter owns creation, streams and cleanup of temporary report artifacts. */
@Component
public class FilesystemReportWorkspace implements ReportWorkspace {
    private static final Logger log = LoggerFactory.getLogger(FilesystemReportWorkspace.class);

    @Override
    public TemporaryReport create(UUID reportId, ReportFormat format) throws IOException {
        return new TemporaryFile(Files.createTempFile("report-" + reportId, "." + format.extension()));
    }

    private record TemporaryFile(Path path) implements TemporaryReport {
        @Override public OutputStream write() throws IOException { return Files.newOutputStream(path); }
        @Override public InputStream read() throws IOException { return Files.newInputStream(path); }
        @Override public long size() throws IOException { return Files.size(path); }
        @Override public void close() {
            try {
                Files.deleteIfExists(path);
            } catch (IOException error) {
                log.warn("Temporary report file was not removed: file={}", path, error);
            }
        }
    }
}
