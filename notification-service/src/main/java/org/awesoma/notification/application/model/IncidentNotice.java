package org.awesoma.notification.application.model;

import org.awesoma.notification.domain.IncidentKind;
public record IncidentNotice(Long incidentId, Long projectId, IncidentKind kind) { }
