package org.awesoma.monitoring.application.port.command;

import java.util.Set;
import org.awesoma.monitoring.domain.enums.HttpMethod;

public interface MonitorCreateRequest {
    String name();
    String url();
    HttpMethod httpMethod();
    Integer intervalSec();
    Integer timeoutMs();
    Integer expectedStatus();
    Set<String> tags();
}
