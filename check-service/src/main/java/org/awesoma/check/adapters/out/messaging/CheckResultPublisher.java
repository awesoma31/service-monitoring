package org.awesoma.check.adapters.out.messaging;

import org.awesoma.integration.IntegrationEvent;
import reactor.core.publisher.Mono;

public interface CheckResultPublisher {
    String transport();
    Mono<Void> publish(IntegrationEvent event);
}
