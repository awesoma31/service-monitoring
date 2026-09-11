package org.awesoma.monitoring.domain.enums;

/** An incident is OPEN from the first failed probe until the monitor recovers. */
public enum IncidentStatus {
    OPEN,
    RESOLVED
}
