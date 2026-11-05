package org.awesoma.check.adapters.out.http;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.awesoma.check.application.port.MonitorOwnership;
import org.awesoma.check.application.exception.ServiceUnavailableException;
import org.awesoma.check.client.ReportDataMonitorClient;
import org.awesoma.integration.reportdata.ReportDataRequests.MonitorOwnershipRequest;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@RequiredArgsConstructor
public class FeignMonitorOwnership implements MonitorOwnership {
    private final ReportDataMonitorClient client;
    public Mono<Decision> ownership(long projectId, List<Long> ids) {
        return Mono.fromCallable(() -> Decision.valueOf(client.ownership(projectId, new MonitorOwnershipRequest(ids)).ownership().name()))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(error -> new ServiceUnavailableException("monitor-service", error));
    }
}
