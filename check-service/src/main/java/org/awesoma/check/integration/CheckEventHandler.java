package org.awesoma.check.integration;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.awesoma.check.application.model.DeletionEvent;
import org.awesoma.check.application.usecase.ResourceDeletionService;
import org.awesoma.integration.IntegrationEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Adapts integration envelopes into the resource deletion application operation. */
@Component
@RequiredArgsConstructor
public class CheckEventHandler {
    private final ResourceDeletionService deletions;

    public Mono<Void> handle(IntegrationEvent event) {
        return Mono.defer(() -> {
            if (event == null) throw new IllegalArgumentException("Missing event");
            event.validate();
            List<Long> monitors = switch (event.type()) {
                case IntegrationEvent.MONITOR_DELETED -> List.of(event.monitorId());
                case IntegrationEvent.PROJECT_DELETED -> event.monitorIds();
                default -> throw new IllegalArgumentException("Unexpected event type");
            };
            return deletions.handle(new DeletionEvent(event.eventId(), monitors));
        });
    }

    public Mono<Void> deleteMonitorLegacy(Long monitorId) {
        return deletions.deleteMonitor(monitorId);
    }
}
