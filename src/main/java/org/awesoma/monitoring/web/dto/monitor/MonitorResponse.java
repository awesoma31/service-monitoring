package org.awesoma.monitoring.web.dto.monitor;

import java.util.Set;
import org.awesoma.monitoring.domain.enums.HttpMethod;
import org.awesoma.monitoring.domain.enums.MonitorState;

public record MonitorResponse(
        Long id,
        Long projectId,
        String name,
        String url,
        HttpMethod httpMethod,
        int intervalSec,
        int timeoutMs,
        int expectedStatus,
        boolean active,
        MonitorState currentState,
        Set<String> tags) {
}
