package org.awesoma.user.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("security.clients")
public record ServiceClientsProperties(Map<String, String> secrets) {
    public ServiceClientsProperties {
        secrets = secrets == null ? Map.of() : Map.copyOf(secrets);
    }
}
