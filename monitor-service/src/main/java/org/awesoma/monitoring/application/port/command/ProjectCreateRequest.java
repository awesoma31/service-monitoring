package org.awesoma.monitoring.application.port.command;


public interface ProjectCreateRequest {
    Long ownerId();
    String name();
    String slug();
    Boolean ownerNotificationsEnabled();
}
