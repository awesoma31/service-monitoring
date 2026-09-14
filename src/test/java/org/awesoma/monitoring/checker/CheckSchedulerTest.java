package org.awesoma.monitoring.checker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.awesoma.monitoring.domain.enums.HttpMethod;
import org.awesoma.monitoring.domain.model.MonitorTarget;
import org.awesoma.monitoring.domain.model.ProbeOutcome;
import org.awesoma.monitoring.service.MonitorCheckService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CheckSchedulerTest {

    @Mock private MonitorCheckService checks;
    @Mock private MonitorProbe probe;
    @InjectMocks private CheckScheduler scheduler;

    @BeforeEach
    void setBatchSize() {
        ReflectionTestUtils.setField(scheduler, "batchSize", 10);
    }

    @Test
    void probesAndRecordsEveryDueMonitor() {
        when(checks.findDueTargets(10)).thenReturn(List.of(target(1L), target(2L)));
        when(probe.probe(any())).thenReturn(ProbeOutcome.success(10, 200));

        scheduler.runDueChecks();

        verify(checks).record(eq(1L), any());
        verify(checks).record(eq(2L), any());
    }

    @Test
    void oneFailingMonitorDoesNotStopTheRestOfTheBatch() {
        when(checks.findDueTargets(10)).thenReturn(List.of(target(1L), target(2L)));
        when(probe.probe(any())).thenReturn(ProbeOutcome.success(10, 200));
        doThrow(new IllegalStateException("database hiccup")).when(checks).record(eq(1L), any());

        scheduler.runDueChecks();

        verify(checks).record(eq(2L), any());
    }

    @Test
    void doesNothingWhenNoMonitorIsDue() {
        when(checks.findDueTargets(10)).thenReturn(List.of());

        scheduler.runDueChecks();

        verifyNoInteractions(probe);
    }

    private MonitorTarget target(long id) {
        return new MonitorTarget(id, "https://example.com", HttpMethod.GET, 5000, 200);
    }
}
