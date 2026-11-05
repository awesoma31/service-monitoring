package org.awesoma.notification.application.port;

import reactor.core.publisher.Mono;
public interface MonitorDirectory {
    Mono<Boolean> projectExists(Long id);
    Mono<Boolean> incidentExists(Long id);
}
