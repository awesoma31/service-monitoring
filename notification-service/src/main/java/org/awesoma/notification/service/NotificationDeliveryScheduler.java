package org.awesoma.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "notifications.delivery.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class NotificationDeliveryScheduler {

    private final NotificationDeliveryService deliveryService;

    @Scheduled(fixedDelayString = "${notifications.delivery.interval-ms:5000}")
    public void dispatch() {
        try {
            Integer claimed = deliveryService.dispatchOnce().block();
            if (claimed != null && claimed > 0) {
                log.debug("Claimed {} notifications for delivery", claimed);
            }
        } catch (RuntimeException exception) {
            log.error("Notification delivery cycle failed", exception);
        }
    }
}
