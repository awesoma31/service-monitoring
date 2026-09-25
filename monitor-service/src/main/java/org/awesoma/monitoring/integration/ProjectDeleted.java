package org.awesoma.monitoring.integration;

import java.util.List;

/**
 * The monitors go with the project in this database; their history in check-service and the
 * project's channels in notification-service are removed after commit.
 */
public record ProjectDeleted(Long projectId, List<Long> monitorIds) {
}
