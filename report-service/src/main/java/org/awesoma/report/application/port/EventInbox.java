package org.awesoma.report.application.port;

import java.util.UUID;

/** IDs of handled events, recorded in the same transaction as their effect. */
public interface EventInbox {

    /** @return {@code false} when the event was handled before */
    boolean record(UUID eventId);
}
