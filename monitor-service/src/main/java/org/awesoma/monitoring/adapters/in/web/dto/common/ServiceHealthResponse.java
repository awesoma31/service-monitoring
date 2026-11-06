package org.awesoma.monitoring.adapters.in.web.dto.common;

public record ServiceHealthResponse(
        String service, String status, boolean fallback, String circuitBreakerState) {

    public boolean available() {
        return !fallback && "UP".equals(status);
    }
}
