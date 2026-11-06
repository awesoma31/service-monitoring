package org.awesoma.notification.config;

import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class IntegrationTransportValidation {
    private final Environment environment;

    @PostConstruct
    void validate() {
        for (String flow : List.of("check-results", "incident-events", "resource-events", "report-events")) {
            String key = "integration." + flow + ".transport";
            String value = environment.getProperty(key, "http");
            if (!List.of("http", "kafka").contains(value)) {
                throw new IllegalArgumentException(key + " must be http or kafka");
            }
        }
    }
}
