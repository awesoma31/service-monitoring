package org.awesoma.notification.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("notifications.delivery")
public record DeliveryProperties(
        boolean enabled,
        int batchSize,
        int concurrency,
        Duration leaseDuration,
        int maxAttempts,
        Duration retryDelay) {

    public DeliveryProperties {
        if (batchSize < 1 || batchSize > 50) {
            throw new IllegalArgumentException("notifications.delivery.batch-size must be between 1 and 50");
        }
        if (concurrency < 1 || concurrency > 50) {
            throw new IllegalArgumentException("notifications.delivery.concurrency must be between 1 and 50");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("notifications.delivery.max-attempts must be positive");
        }
        if (leaseDuration == null || leaseDuration.isNegative() || leaseDuration.isZero()) {
            throw new IllegalArgumentException("notifications.delivery.lease-duration must be positive");
        }
        if (retryDelay == null || retryDelay.isNegative()) {
            throw new IllegalArgumentException("notifications.delivery.retry-delay must not be negative");
        }
    }
}
