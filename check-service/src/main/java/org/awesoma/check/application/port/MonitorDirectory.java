package org.awesoma.check.application.port;

import org.awesoma.check.domain.MonitorTarget;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MonitorDirectory {
    Flux<MonitorTarget> due(int limit, int concurrency);
    Mono<Boolean> exists(Long monitorId);
}
