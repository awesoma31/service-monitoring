package org.awesoma.check.domain;

/**
 * What monitor-service reports for a monitor that is due. A copy of its type rather than a
 * shared library: the services are coupled by the JSON contract only, not by their builds.
 */
public record MonitorTarget(
        Long monitorId, String url, HttpMethod httpMethod, int timeoutMs, int expectedStatus) {
}
