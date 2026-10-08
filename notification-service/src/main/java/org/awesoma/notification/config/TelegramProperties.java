package org.awesoma.notification.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("notifications.telegram")
public record TelegramProperties(
        boolean enabled,
        String botToken,
        String apiBaseUrl,
        String proxyHost,
        Integer proxyPort,
        Duration requestTimeout) {

    public TelegramProperties {
        if (requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()) {
            throw new IllegalArgumentException("notifications.telegram.request-timeout must be positive");
        }
        if (proxyHost != null && !proxyHost.isBlank() && (proxyPort == null || proxyPort < 1 || proxyPort > 65_535)) {
            throw new IllegalArgumentException("notifications.telegram.proxy-port must be a valid TCP port");
        }
    }
}
