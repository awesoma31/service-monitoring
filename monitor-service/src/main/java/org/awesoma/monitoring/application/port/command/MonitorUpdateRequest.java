package org.awesoma.monitoring.application.port.command;

import org.awesoma.monitoring.domain.enums.HttpMethod;

public interface MonitorUpdateRequest {
    String name();
    String url();
    HttpMethod httpMethod();
    int intervalSec();
    int timeoutMs();
    int expectedStatus();
    boolean active();
}
