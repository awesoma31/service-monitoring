package org.awesoma.monitoring.web.dto.incident;

import java.time.OffsetDateTime;
import org.awesoma.monitoring.domain.enums.ChannelType;
import org.awesoma.monitoring.domain.enums.NotificationStatus;

public record NotificationResponse(
        Long id,
        Long incidentId,
        Long channelId,
        ChannelType channelType,
        String target,
        OffsetDateTime sentAt,
        NotificationStatus status,
        int attempts) {
}
