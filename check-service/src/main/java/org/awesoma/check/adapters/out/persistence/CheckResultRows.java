package org.awesoma.check.adapters.out.persistence;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CheckResultRows extends ReactiveCrudRepository<CheckResultRow, Long> {
    @Query("""
        SELECT * FROM check_results WHERE monitor_id = :monitorId
        ORDER BY checked_at DESC, id DESC LIMIT :limit OFFSET :offset
        """)
    Flux<CheckResultRow> findPage(Long monitorId, int limit, long offset);
    Mono<Long> deleteByMonitorId(Long monitorId);
}
