package org.awesoma.monitoring.web.dto.project;

import java.time.OffsetDateTime;

public record ProjectResponse(
        Long id, Long ownerId, String name, String slug, OffsetDateTime createdAt) {
}
