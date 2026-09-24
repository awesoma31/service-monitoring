package org.awesoma.monitoring.repository;

import org.awesoma.monitoring.domain.entity.CheckResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckResultRepository extends JpaRepository<CheckResult, Long> {

    /**
     * History for infinite scrolling. A Slice deliberately avoids the count query: the
     * table grows without bound and the client only needs to know whether more rows follow.
     */
    Slice<CheckResult> findByMonitorIdOrderByCheckedAtDesc(Long monitorId, Pageable pageable);
}
