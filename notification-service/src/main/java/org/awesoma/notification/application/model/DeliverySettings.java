package org.awesoma.notification.application.model;

import java.time.Duration;
public record DeliverySettings(int batchSize, int concurrency, Duration leaseDuration, int maxAttempts,
                               Duration retryDelay) { }
