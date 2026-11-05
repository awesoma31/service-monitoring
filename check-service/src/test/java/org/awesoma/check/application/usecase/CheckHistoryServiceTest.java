package org.awesoma.check.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import org.awesoma.check.application.exception.NotFoundException;
import org.awesoma.check.application.port.CheckHistory;
import org.awesoma.check.application.port.MonitorDirectory;
import org.awesoma.check.domain.CheckResult;
import org.awesoma.check.domain.ProbeOutcome;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CheckHistoryServiceTest {
    private final CheckHistory history = mock(CheckHistory.class);
    private final MonitorDirectory monitors = mock(MonitorDirectory.class);
    private final CheckHistoryService service = new CheckHistoryService(history, monitors);

    @Test
    void asksForOneExtraRowAndReturnsTheRequestedSlice() {
        when(monitors.exists(1L)).thenReturn(Mono.just(true));
        var first = CheckResult.of(1L, ProbeOutcome.success(2, 200), OffsetDateTime.now());
        var second = CheckResult.of(1L, ProbeOutcome.success(3, 200), OffsetDateTime.now());
        when(history.findPage(1L, 2, 2)).thenReturn(Flux.just(first, second));

        StepVerifier.create(service.list(1L, 2, 1)).assertNext(slice -> {
            assertThat(slice.items()).containsExactly(first);
            assertThat(slice.hasNext()).isTrue();
        }).verifyComplete();
        verify(history).findPage(1L, 2, 2);
    }

    @Test
    void returnsNotFoundForADeletedMonitor() {
        when(monitors.exists(5L)).thenReturn(Mono.just(false));
        StepVerifier.create(service.list(5L, 0, 20)).expectError(NotFoundException.class).verify();
    }
}
