package org.awesoma.report.application.port;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import org.awesoma.report.domain.ReportFormat;

/** Temporary, bounded-by-caller storage for rendering one report attempt. */
public interface ReportWorkspace {
    TemporaryReport create(UUID reportId, ReportFormat format) throws IOException;

    interface TemporaryReport extends AutoCloseable {
        OutputStream write() throws IOException;
        InputStream read() throws IOException;
        long size() throws IOException;
        @Override void close();
    }
}
