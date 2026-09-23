package org.awesoma.monitoring.repository;

import org.awesoma.monitoring.domain.entity.CheckResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckResultRepository extends JpaRepository<CheckResult, Long> {

    Slice<CheckResult> findByMonitorIdOrderByCheckedAtDesc(Long monitorId, Pageable pageable);
}
