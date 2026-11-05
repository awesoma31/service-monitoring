package org.awesoma.check.repository;

import lombok.RequiredArgsConstructor;
import org.awesoma.check.adapters.out.persistence.CheckResultRow;
import org.awesoma.check.adapters.out.persistence.CheckResultRows;
import org.awesoma.check.application.port.CheckHistory;
import org.awesoma.check.domain.CheckResult;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** R2DBC persistence adapter for the check-history port. */
@Component
@RequiredArgsConstructor
public class CheckResultRepository implements CheckHistory {
    private final CheckResultRows rows;
    public Mono<CheckResult> save(CheckResult result) {
        return rows.save(CheckResultRow.from(result)).map(CheckResultRow::toDomain);
    }
    public Flux<CheckResult> findPage(Long monitorId, int limit, long offset) {
        return rows.findPage(monitorId, limit, offset).map(CheckResultRow::toDomain);
    }
    public Mono<Long> deleteByMonitorId(Long monitorId) { return rows.deleteByMonitorId(monitorId); }
}
