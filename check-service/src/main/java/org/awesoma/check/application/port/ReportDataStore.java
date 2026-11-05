package org.awesoma.check.application.port;

import org.awesoma.check.application.model.ReportCheck;
import org.awesoma.check.application.model.ReportPosition;
import org.awesoma.check.application.model.ReportQuery;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ReportDataStore {
    Mono<Long> upperId();
    Flux<ReportCheck> find(ReportQuery request, ReportPosition cursor);
}
