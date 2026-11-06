package org.awesoma.check.application.port;

import org.awesoma.integration.reportdata.ReportDataCursor;
import org.awesoma.integration.reportdata.ReportDataRequests.PeriodSearchRequest;
import org.awesoma.integration.reportdata.ReportDataResponses.CheckItem;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ReportDataStore {
    Mono<Long> upperId();

    /** Up to {@code limit + 1} rows after the cursor, so that the caller can tell whether more follow. */
    Flux<CheckItem> find(PeriodSearchRequest request, ReportDataCursor cursor);
}
