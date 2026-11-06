package org.awesoma.check.application.model;

import java.util.List;
import java.util.UUID;

/** An already validated request to delete histories belonging to removed monitors. */
public record DeletionEvent(UUID id, List<Long> monitorIds) {
    public DeletionEvent { monitorIds = List.copyOf(monitorIds); }
}
