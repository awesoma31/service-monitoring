package org.awesoma.check.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.awesoma.check.domain.ProbeOutcome;
import org.awesoma.check.web.exception.ServiceUnavailableException;
import org.junit.jupiter.api.Test;

class MonitorClientFallbackFactoryTest {

    private final MonitorClient fallback =
            new MonitorClientFallbackFactory().create(new IllegalStateException("connection refused"));

    @Test
    void noMonitorIsDueWhileMonitorServiceIsDown() {
        assertThat(fallback.due(50)).isEmpty();
    }

    @Test
    void anOutcomeThatCannotBeReportedIsDroppedWithoutFailing() {
        assertThatCode(() -> fallback.report(1L, ProbeOutcome.success(10, 200))).doesNotThrowAnyException();
    }

    @Test
    void existenceCannotBeAnsweredSoTheCallerGetsAnUnavailableError() {
        assertThatThrownBy(() -> fallback.exists(1L))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("monitor-service");
    }
}
