package org.awesoma.check.application.port;

import org.awesoma.check.domain.MonitorTarget;
import org.awesoma.check.domain.ProbeOutcome;
import reactor.core.publisher.Mono;

public interface MonitorProbe {
    Mono<ProbeOutcome> probe(MonitorTarget target);
}
