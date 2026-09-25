package org.awesoma.notification.web.dto;

import java.time.OffsetDateTime;
import org.awesoma.notification.domain.ChannelType;
import org.awesoma.notification.domain.Notification;
import org.awesoma.notification.domain.NotificationStatus;

public record NotificationResponse(
        Long id,
        Long incidentId,
        Long channelId,
        ChannelType channelType,
        String target,
        OffsetDateTime sentAt,
        NotificationStatus status,
        int attempts) {

    public static NotificationResponse of(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getIncidentId(),
                notification.getChannel().getId(),
                notification.getChannel().getType(),
                notification.getChannel().getTarget(),
                notification.getSentAt(),
                notification.getStatus(),
                notification.getAttempts());
    }
}
