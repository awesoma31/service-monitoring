package org.awesoma.monitoring.domain.model;

import org.awesoma.monitoring.domain.enums.HttpMethod;

/**
 * What the checker needs to probe a monitor — deliberately not the entity, so the checker
 * never touches persistence and can be lifted out into its own service unchanged.
 */
public record MonitorTarget(
        Long monitorId, String url, HttpMethod httpMethod, int timeoutMs, int expectedStatus) {
}
