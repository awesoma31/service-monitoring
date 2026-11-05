package org.awesoma.monitoring.application.event;

/** The check history of the monitor, kept by check-service, is removed after commit. */
public record MonitorDeleted(Long monitorId) {
}
