package org.awesoma.monitoring.domain.enums;

/** Last known state of a monitor, updated by the checker. UNKNOWN until the first probe. */
public enum MonitorState {
    UP,
    DOWN,
    PAUSED,
    UNKNOWN
}
