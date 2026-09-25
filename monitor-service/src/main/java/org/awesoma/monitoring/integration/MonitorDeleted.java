package org.awesoma.monitoring.integration;

/** The check history of the monitor, kept by check-service, is removed after commit. */
public record MonitorDeleted(Long monitorId) {
}
