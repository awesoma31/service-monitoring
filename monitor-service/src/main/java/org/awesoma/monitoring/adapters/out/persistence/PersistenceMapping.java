package org.awesoma.monitoring.adapters.out.persistence;

import java.util.LinkedHashSet;
import org.awesoma.monitoring.application.model.Page;
import org.awesoma.monitoring.application.model.PageRequest;
import org.awesoma.monitoring.domain.entity.Project;
import org.awesoma.monitoring.domain.entity.Tag;
import org.awesoma.monitoring.domain.entity.Monitor;
import org.awesoma.monitoring.domain.entity.Incident;
import org.awesoma.monitoring.domain.entity.ProjectMember;

/** Explicit mapping across the persistence boundary. No JPA type escapes an adapter. */
final class PersistenceMapping {
    private PersistenceMapping() {}
    static org.springframework.data.domain.Pageable pageable(PageRequest page) {
    return org.springframework.data.domain.PageRequest.of(page.page(),page.size());
    }
    static <S,T> Page<T> page(org.springframework.data.domain.Page<S> input, java.util.function.Function<S,T> mapper) {
    return new Page<>(input.getContent().stream().map(mapper).toList(),input.getNumber(),input.getSize(),input.getTotalElements());
    }
    static Project project(org.awesoma.monitoring.adapters.out.persistence.entity.Project source) {
    if (source == null) return null;
    Project result=new Project();
    result.setId(source.getId());
    result.setOwnerId(source.getOwnerId());
    result.setName(source.getName());
    result.setSlug(source.getSlug());
    result.setCreatedAt(source.getCreatedAt());
    result.setOwnerNotificationsEnabled(source.isOwnerNotificationsEnabled());
    return result;
    }
    static Tag tag(org.awesoma.monitoring.adapters.out.persistence.entity.Tag source) {
    Tag result=new Tag();
    result.setId(source.getId());
    result.setName(source.getName());
    return result;
    }
    static Monitor monitor(org.awesoma.monitoring.adapters.out.persistence.entity.Monitor source) {
    Monitor result=new Monitor();
    result.setId(source.getId());
    result.setProject(project(source.getProject()));
    result.setName(source.getName());
    result.setUrl(source.getUrl());
    result.setHttpMethod(source.getHttpMethod());
    result.setIntervalSec(source.getIntervalSec());
    result.setTimeoutMs(source.getTimeoutMs());
    result.setExpectedStatus(source.getExpectedStatus());
    result.setActive(source.isActive());
    result.setCurrentState(source.getCurrentState());
    result.setLastCheckedAt(source.getLastCheckedAt());
    result.setCheckClaimToken(source.getCheckClaimToken());
    result.setCheckClaimedUntil(source.getCheckClaimedUntil());
    var tags = new LinkedHashSet<Tag>();
    source.getTags().forEach(t -> tags.add(tag(t)));
    result.setTags(tags);
    return result;
    }
    static Incident incident(org.awesoma.monitoring.adapters.out.persistence.entity.Incident source) {
    Incident result=new Incident();
    result.setId(source.getId());
    result.setMonitor(monitor(source.getMonitor()));
    result.setStartedAt(source.getStartedAt());
    result.setResolvedAt(source.getResolvedAt());
    result.setStatus(source.getStatus());
    result.setSeverity(source.getSeverity());
    result.setCause(source.getCause());
    return result;
    }
    static ProjectMember member(org.awesoma.monitoring.adapters.out.persistence.entity.ProjectMember source) {
    ProjectMember result=new ProjectMember(project(source.getProject()),source.getUserId());
    result.setJoinedAt(source.getJoinedAt());
    return result;
    }
}
