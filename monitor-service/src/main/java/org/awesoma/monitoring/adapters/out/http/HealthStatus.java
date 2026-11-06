package org.awesoma.monitoring.adapters.out.http;

/** Actuator's status plus an explicit marker for a locally generated fallback. */
public record HealthStatus(String status, boolean fallback) {

    public static HealthStatus unavailable() {
        return new HealthStatus("UNAVAILABLE", true);
    }
}
