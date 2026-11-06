package org.awesoma.monitoring.application.model.view;

import java.time.OffsetDateTime;

public record ProjectResponse(
        Long id,
        Long ownerId,
        String name,
        String slug,
        OffsetDateTime createdAt,
        boolean ownerNotificationsEnabled) {
}
