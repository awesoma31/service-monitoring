package org.awesoma.report.application.port;

import java.util.List;
import org.awesoma.report.domain.Report;

public record ReportPage(List<Report> items, long total, boolean hasNext) {

    public ReportPage {
        items = List.copyOf(items);
    }
}
