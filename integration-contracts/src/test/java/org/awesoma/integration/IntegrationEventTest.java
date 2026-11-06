package org.awesoma.integration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IntegrationEventTest {
    private final UUID id = UUID.randomUUID();
    private final OffsetDateTime now = OffsetDateTime.now();

    @Test
    void acceptsKnownValidEvents() {
        var outcome = new IntegrationEvent.CheckOutcome("SUCCESS", 12, 200, null, UUID.randomUUID());
        for (var event : List.of(
                new IntegrationEvent(id, 1, "CheckCompleted", now, 1L, null, null, null, null, outcome),
                new IntegrationEvent(id, 1, "IncidentChanged", now, null, 2L, 3L, "OPENED", null, null),
                new IntegrationEvent(id, 1, "MonitorDeleted", now, 1L, null, null, null, null, null),
                new IntegrationEvent(id, 1, "ProjectDeleted", now, null, 2L, null, null, List.of(1L), null))) {
            assertDoesNotThrow(event::validate);
        }
    }

    @Test
    void rejectsInvalidEnvelopeAndPayload() {
        assertThrows(IllegalArgumentException.class, () -> new IntegrationEvent(
                id, 2, "MonitorDeleted", now, 1L, null, null, null, null, null).validate());
        assertThrows(IllegalArgumentException.class, () -> new IntegrationEvent(
                id, 1, "IncidentChanged", now, null, 2L, 3L, "OTHER", null, null).validate());
        assertThrows(IllegalArgumentException.class, () -> new IntegrationEvent(
                id, 1, "CheckCompleted", now, 1L, null, null, null, null,
                new IntegrationEvent.CheckOutcome("SUCCESS", -1, 200, null, id)).validate());
        assertThrows(IllegalArgumentException.class, () -> new IntegrationEvent(
                id, 1, "Unknown", now, null, null, null, null, null, null).validate());
    }
}
