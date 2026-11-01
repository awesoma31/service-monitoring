package org.awesoma.check.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.awesoma.check.client.ReactiveMonitorClient;
import org.awesoma.check.integration.CheckResultRecorder;
import org.awesoma.check.domain.HttpMethod;
import org.awesoma.check.domain.MonitorTarget;
import org.awesoma.check.domain.ProbeOutcome;
import org.awesoma.check.probe.MonitorProbe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CheckRunnerTest {

    private final ReactiveMonitorClient monitors = mock(ReactiveMonitorClient.class);
    private final MonitorProbe probe = mock(MonitorProbe.class);
    private final CheckResultRecorder results = mock(CheckResultRecorder.class);
    private final CheckRunner runner = new CheckRunner(monitors, probe, results, 10, 4);

    @BeforeEach
    void storeWhateverIsSaved() {
        when(results.record(any(), any(), any())).thenReturn(Mono.empty());
    }

    @Test
    void storesTheResultBeforeReportingIt() {
        ProbeOutcome outcome = ProbeOutcome.success(12, 200);
        MonitorTarget target = target(1L);
        when(monitors.due(10, 4)).thenReturn(Flux.just(target));
        when(probe.probe(target)).thenReturn(Mono.just(outcome));

        StepVerifier.create(runner.runOnce()).verifyComplete();

        verify(results).record(eq(1L), eq(outcome.forClaim(target.claimToken())), any());
    }

    @Test
    void oneFailingMonitorDoesNotStopTheRestOfTheBatch() {
        MonitorTarget first = target(1L);
        MonitorTarget second = target(2L);
        when(monitors.due(10, 4)).thenReturn(Flux.just(first, second));
        when(probe.probe(first)).thenReturn(Mono.error(new IllegalStateException("broken")));
        when(probe.probe(second)).thenReturn(Mono.just(ProbeOutcome.success(5, 200)));

        StepVerifier.create(runner.runOnce()).verifyComplete();

        verify(results).record(eq(2L), any(), any());
        verify(results, never()).record(eq(1L), any(), any());
    }

    @Test
    void doesNothingWhenNoMonitorIsDue() {
        when(monitors.due(10, 4)).thenReturn(Flux.empty());

        StepVerifier.create(runner.runOnce()).verifyComplete();

        verify(probe, never()).probe(any());
        verify(results, never()).record(any(), any(), any());
    }

    private MonitorTarget target(Long id) {
        return new MonitorTarget(
                id, "https://example.org", HttpMethod.GET, 1000, 200, UUID.randomUUID());
    }
}
