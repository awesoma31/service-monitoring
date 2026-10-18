package org.awesoma.monitoring.service;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import org.awesoma.monitoring.integration.CheckHistoryClient;
import org.awesoma.monitoring.integration.HealthStatus;
import org.awesoma.monitoring.integration.NotificationClient;
import org.awesoma.monitoring.web.dto.common.ServiceHealthResponse;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ServiceHealthService {

    private final CheckHistoryClient checks;
    private final NotificationClient notifications;
    private final CircuitBreakerRegistry circuitBreakers;

    public ServiceHealthResponse checkService() {
        return response("check-service", checks.health(), "CheckHistoryClienthealth");
    }

    public ServiceHealthResponse notificationService() {
        return response("notification-service", notifications.health(), "NotificationClienthealth");
    }

    private ServiceHealthResponse response(String service, HealthStatus health, String breakerName) {
        // OpenFeign's default alphanumeric IDs are <interface><method><parameter types>.
        // Read after the call so the response includes the transition caused by this request.
        String state = circuitBreakers.find(breakerName)
                .map(breaker -> breaker.getState().name())
                .orElse("UNKNOWN");
        return new ServiceHealthResponse(service, health.status(), health.fallback(), state);
    }
}
