package org.awesoma.monitoring.domain.model;

import org.awesoma.monitoring.domain.enums.HttpMethod;

public record MonitorTarget(
        Long monitorId, String url, HttpMethod httpMethod, int timeoutMs, int expectedStatus) {
}
