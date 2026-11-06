package org.awesoma.check.adapters.out.persistence;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.awesoma.check.application.port.ReportDataStore;
import org.awesoma.integration.reportdata.ReportDataCursor;
import org.awesoma.integration.reportdata.ReportDataRequests.PeriodSearchRequest;
import org.awesoma.integration.reportdata.ReportDataResponses.CheckItem;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class R2dbcReportDataStore implements ReportDataStore {
    private final DatabaseClient db;
    @Override
    public Mono<Long> upperId() {
        return db.sql("SELECT COALESCE(MAX(id), 0) AS upper FROM check_results")
                .map(row -> row.get("upper", Long.class)).one();
    }
    @Override
    public Flux<CheckItem> find(PeriodSearchRequest request, ReportDataCursor cursor) {
        return db.sql("""
            SELECT id, monitor_id, result, response_ms, checked_at FROM check_results
            WHERE monitor_id = ANY(:monitorIds) AND checked_at >= :from AND checked_at < :to
              AND id > :after AND id <= :upper ORDER BY id LIMIT :limit
            """)
            .bind("monitorIds", request.monitorIds().toArray(Long[]::new))
            .bind("from", request.from().atOffset(ZoneOffset.UTC))
            .bind("to", request.to().atOffset(ZoneOffset.UTC))
            .bind("after", cursor.lastId()).bind("upper", cursor.upperId()).bind("limit", request.limit() + 1)
            .map(row -> new CheckItem(row.get("id", Long.class), row.get("monitor_id", Long.class),
                    row.get("result", String.class), row.get("response_ms", Integer.class),
                    row.get("checked_at", OffsetDateTime.class).toInstant())).all();
    }
}
