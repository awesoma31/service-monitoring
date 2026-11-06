package org.awesoma.report.application.port;

import java.io.IOException;
import java.io.OutputStream;
import org.awesoma.report.domain.ReportFormat;
import org.awesoma.report.domain.content.ReportContent;

public interface ReportRenderer {

    ReportFormat format();

    /** Writes the whole document; the caller bounds and closes the stream. */
    void render(ReportContent content, OutputStream out) throws IOException;
}
