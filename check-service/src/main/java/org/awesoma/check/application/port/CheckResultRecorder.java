package org.awesoma.check.application.port;

import java.time.OffsetDateTime;
import org.awesoma.check.domain.ProbeOutcome;
import reactor.core.publisher.Mono;

/** Application port for atomically recording a probe and enqueueing its result. */
public interface CheckResultRecorder {
    Mono<Void> record(Long monitorId, ProbeOutcome outcome, OffsetDateTime checkedAt);
}
