package org.awesoma.check.application.port;

import java.util.List;
import reactor.core.publisher.Mono;
public interface MonitorOwnership {
    enum Decision { OWNED, PROJECT_NOT_FOUND, MONITOR_NOT_IN_PROJECT }
    Mono<Decision> ownership(long projectId, List<Long> monitorIds);
}
