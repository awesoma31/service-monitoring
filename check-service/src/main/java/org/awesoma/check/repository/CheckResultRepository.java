package org.awesoma.check.repository;

import org.awesoma.check.domain.CheckResult;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface CheckResultRepository extends ReactiveCrudRepository<CheckResult, Long> {

    /**
     * One page of history, newest first. The caller asks for one row more than it returns:
     * that extra row tells whether another page follows, so no count query is ever needed.
     */
    @Query("""
            SELECT * FROM check_results
            WHERE monitor_id = :monitorId
            ORDER BY checked_at DESC, id DESC
            LIMIT :limit OFFSET :offset
            """)
    Flux<CheckResult> findPage(Long monitorId, int limit, long offset);
}
