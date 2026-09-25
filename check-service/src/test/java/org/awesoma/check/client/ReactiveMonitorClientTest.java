package org.awesoma.check.client;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.awesoma.check.domain.HttpMethod;
import org.awesoma.check.domain.MonitorTarget;
import org.awesoma.check.domain.ProbeOutcome;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class ReactiveMonitorClientTest {

    private final MonitorClient feign = mock(MonitorClient.class);
    private final ReactiveMonitorClient client = new ReactiveMonitorClient(feign);

    @Test
    void emitsEveryDueTarget() {
        MonitorTarget first = new MonitorTarget(1L, "https://a.example", HttpMethod.GET, 1000, 200);
        MonitorTarget second = new MonitorTarget(2L, "https://b.example", HttpMethod.HEAD, 1000, 204);
        when(feign.due(5)).thenReturn(List.of(first, second));

        StepVerifier.create(client.due(5)).expectNext(first, second).verifyComplete();
    }

    @Test
    void reportsAnOutcome() {
        ProbeOutcome outcome = ProbeOutcome.timeout(1000, "No answer within 1000 ms");

        StepVerifier.create(client.report(3L, outcome)).verifyComplete();

        verify(feign).report(3L, outcome);
    }

    @Test
    void answersWhetherAMonitorExists() {
        when(feign.exists(4L)).thenReturn(true);

        StepVerifier.create(client.exists(4L)).expectNext(true).verifyComplete();
    }
}
