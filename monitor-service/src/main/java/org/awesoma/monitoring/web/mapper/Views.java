package org.awesoma.monitoring.web.mapper;

import org.awesoma.monitoring.application.model.Page;
import org.awesoma.monitoring.application.model.view.ProjectResponse;
import org.awesoma.monitoring.application.model.view.ProjectMemberResponse;
import org.awesoma.monitoring.application.model.view.MonitorResponse;
import org.awesoma.monitoring.application.model.view.IncidentResponse;
import org.awesoma.monitoring.application.model.view.TagResponse;
import java.util.function.Function;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/** Converts application views to the existing HTTP response contract. */
public final class Views {
    private Views() {}
    public static org.awesoma.monitoring.web.dto.project.ProjectResponse project(ProjectResponse v) {
    return new org.awesoma.monitoring.web.dto.project.ProjectResponse(v.id(),v.ownerId(),v.name(),v.slug(),v.createdAt(),v.ownerNotificationsEnabled());
    }
    public static org.awesoma.monitoring.web.dto.project.ProjectMemberResponse member(ProjectMemberResponse v) {
    return new org.awesoma.monitoring.web.dto.project.ProjectMemberResponse(v.userId(),v.email(),v.fullName(),v.joinedAt());
    }
    public static org.awesoma.monitoring.web.dto.monitor.MonitorResponse monitor(MonitorResponse v) {
    return new org.awesoma.monitoring.web.dto.monitor.MonitorResponse(v.id(),v.projectId(),v.name(),v.url(),v.httpMethod(),v.intervalSec(),v.timeoutMs(),v.expectedStatus(),v.active(),v.currentState(),v.tags());
    }
    public static org.awesoma.monitoring.web.dto.incident.IncidentResponse incident(IncidentResponse v) {
    return new org.awesoma.monitoring.web.dto.incident.IncidentResponse(v.id(),v.monitorId(),v.startedAt(),v.resolvedAt(),v.status(),v.severity(),v.cause());
    }
    public static org.awesoma.monitoring.web.dto.tag.TagResponse tag(TagResponse v) {
    return new org.awesoma.monitoring.web.dto.tag.TagResponse(v.id(),v.name());
    }
    public static <S,T> org.springframework.data.domain.Page<T> page(Page<S> page,Function<S,T> mapper) {
    return new PageImpl<>(page.content().stream().map(mapper).toList(), PageRequest.of(page.page(),page.size()),page.totalElements());
    }
}
