package org.awesoma.check.application.port;

import org.awesoma.check.domain.CheckResult;
import reactor.core.publisher.Flux;

public interface CheckHistory {
    Flux<CheckResult> findPage(Long monitorId, int limit, long offset);
}
