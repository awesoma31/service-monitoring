package org.awesoma.monitoring.application.port;

import org.awesoma.monitoring.domain.entity.Incident;
import org.awesoma.monitoring.application.model.view.IncidentResponse;
public interface IncidentPresenter {
    IncidentResponse toResponse(Incident incident);
}
