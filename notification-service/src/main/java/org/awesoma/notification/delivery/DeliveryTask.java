package org.awesoma.notification.delivery;

import java.util.UUID;
import org.awesoma.notification.domain.ChannelType;

public record DeliveryTask(
        Long notificationId,
        UUID claimToken,
        ChannelType channelType,
        String target,
        String subject,
        String message,
        int attempts) {}
