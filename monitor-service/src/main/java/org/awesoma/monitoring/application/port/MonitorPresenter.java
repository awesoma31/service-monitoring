package org.awesoma.monitoring.application.port;

import org.awesoma.monitoring.domain.entity.Monitor;
import org.awesoma.monitoring.application.model.view.MonitorResponse;
public interface MonitorPresenter {
    MonitorResponse toResponse(Monitor monitor);
}
