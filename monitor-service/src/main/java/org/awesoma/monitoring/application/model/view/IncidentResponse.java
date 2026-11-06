package org.awesoma.monitoring.application.model.view;

import java.time.OffsetDateTime;
import org.awesoma.monitoring.domain.enums.IncidentStatus;
import org.awesoma.monitoring.domain.enums.Severity;

public record IncidentResponse(
        Long id,
        Long monitorId,
        OffsetDateTime startedAt,
        OffsetDateTime resolvedAt,
        IncidentStatus status,
        Severity severity,
        String cause) {
}
