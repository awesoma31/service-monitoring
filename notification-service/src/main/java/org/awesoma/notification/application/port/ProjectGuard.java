package org.awesoma.notification.application.port;

import java.util.UUID;
public interface ProjectGuard {
    void lock(Long projectId);
    boolean deleted(Long projectId);
    void markDeleted(Long projectId);
    boolean recordEvent(UUID eventId);
}
