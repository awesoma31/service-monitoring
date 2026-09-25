package org.awesoma.check.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.awesoma.check.client.ReactiveMonitorClient;
import org.awesoma.check.domain.CheckResult;
import org.awesoma.check.domain.HttpMethod;
import org.awesoma.check.domain.MonitorTarget;
import org.awesoma.check.domain.ProbeOutcome;
import org.awesoma.check.probe.MonitorProbe;
import org.awesoma.check.repository.CheckResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CheckRunnerTest {

    private final ReactiveMonitorClient monitors = mock(ReactiveMonitorClient.class);
    private final MonitorProbe probe = mock(MonitorProbe.class);
    private final CheckResultRepository results = mock(CheckResultRepository.class);
    private final CheckRunner runner = new CheckRunner(monitors, probe, results, 10, 4);

    @BeforeEach
    void storeWhateverIsSaved() {
        when(results.save(any())).thenAnswer(call -> Mono.just(call.getArgument(0, CheckResult.class)));
        when(monitors.report(any(), any())).thenReturn(Mono.empty());
    }

    @Test
    void storesTheResultBeforeReportingIt() {
        ProbeOutcome outcome = ProbeOutcome.success(12, 200);
        when(monitors.due(10)).thenReturn(Flux.just(target(1L)));
        when(probe.probe(target(1L))).thenReturn(Mono.just(outcome));

        StepVerifier.create(runner.runOnce()).verifyComplete();

        InOrder order = Mockito.inOrder(results, monitors);
        order.verify(results).save(any());
        order.verify(monitors).report(1L, outcome);
    }

    @Test
    void oneFailingMonitorDoesNotStopTheRestOfTheBatch() {
        when(monitors.due(10)).thenReturn(Flux.just(target(1L), target(2L)));
        when(probe.probe(target(1L))).thenReturn(Mono.error(new IllegalStateException("broken")));
        when(probe.probe(target(2L))).thenReturn(Mono.just(ProbeOutcome.success(5, 200)));

        StepVerifier.create(runner.runOnce()).verifyComplete();

        verify(monitors).report(eq(2L), any());
        verify(monitors, never()).report(eq(1L), any());
    }

    @Test
    void doesNothingWhenNoMonitorIsDue() {
        when(monitors.due(10)).thenReturn(Flux.empty());

        StepVerifier.create(runner.runOnce()).verifyComplete();

        verify(probe, never()).probe(any());
        verify(results, never()).save(any());
    }

    private MonitorTarget target(Long id) {
        return new MonitorTarget(id, "https://example.org", HttpMethod.GET, 1000, 200);
    }
}
