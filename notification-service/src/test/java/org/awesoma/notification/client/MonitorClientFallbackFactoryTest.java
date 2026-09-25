package org.awesoma.notification.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.awesoma.notification.web.exception.ServiceUnavailableException;
import org.junit.jupiter.api.Test;

class MonitorClientFallbackFactoryTest {

    private final MonitorClient fallback =
            new MonitorClientFallbackFactory().create(new IllegalStateException("connection refused"));

    @Test
    void projectsCannotBeCheckedWhileMonitorServiceIsDown() {
        assertThatThrownBy(() -> fallback.projectExists(1L)).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void incidentsCannotBeCheckedWhileMonitorServiceIsDown() {
        assertThatThrownBy(() -> fallback.incidentExists(1L)).isInstanceOf(ServiceUnavailableException.class);
    }
}
