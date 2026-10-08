package org.awesoma.notification.web.dto;

import java.time.OffsetDateTime;
import org.awesoma.notification.domain.ChannelType;
import org.awesoma.notification.domain.IncidentKind;
import org.awesoma.notification.domain.Notification;
import org.awesoma.notification.domain.NotificationStatus;

public record NotificationResponse(
        Long id,
        Long incidentId,
        Long channelId,
        ChannelType channelType,
        String target,
        IncidentKind incidentKind,
        String subject,
        String message,
        OffsetDateTime sentAt,
        NotificationStatus status,
        int attempts,
        OffsetDateTime nextAttemptAt,
        String lastError) {

    public static NotificationResponse of(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getIncidentId(),
                notification.getChannel().getId(),
                notification.getChannel().getType(),
                notification.getChannel().getTarget(),
                notification.getIncidentKind(),
                notification.getSubject(),
                notification.getMessage(),
                notification.getSentAt(),
                notification.getStatus(),
                notification.getAttempts(),
                notification.getNextAttemptAt(),
                notification.getLastError());
    }
}
