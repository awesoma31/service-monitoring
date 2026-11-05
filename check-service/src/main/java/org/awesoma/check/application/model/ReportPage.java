package org.awesoma.check.application.model;

import java.time.Instant;
import java.util.List;
public record ReportPage(List<ReportCheck> items, String nextCursor, boolean hasNext, Instant sourceCapturedAt,
                         long upperId) {
    public ReportPage { items = List.copyOf(items); }
}
