package org.awesoma.monitoring.domain.entity;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.awesoma.monitoring.domain.enums.IncidentStatus;
import org.awesoma.monitoring.domain.enums.Severity;

@Getter
@Setter
@NoArgsConstructor
public class Incident extends BaseEntity {

    private Monitor monitor;

    private OffsetDateTime startedAt;

    private OffsetDateTime resolvedAt;

    private IncidentStatus status = IncidentStatus.OPEN;

    private Severity severity;

    private String cause;

    public boolean isOpen() {
        return status == IncidentStatus.OPEN;
    }

    /**
     * Closes the incident, moving both fields together. The database rejects a RESOLVED
     * incident without a resolution time, so they must never be set separately.
     */
    public void resolve(OffsetDateTime at) {
        this.status = IncidentStatus.RESOLVED;
        this.resolvedAt = at;
    }
}
