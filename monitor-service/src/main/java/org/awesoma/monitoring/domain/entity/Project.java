package org.awesoma.monitoring.domain.entity;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Project extends BaseEntity {

    private Long ownerId;

    private String name;

    private String slug;

    private OffsetDateTime createdAt;

    /** Whether incident changes should be announced through this project's channels. */
    private boolean ownerNotificationsEnabled = true;

    private Set<ProjectMember> members = new LinkedHashSet<>();

    /**
     * Adds a member and keeps both sides of the association consistent, so a project
     * created and populated in one transaction is persisted with its members intact.
     */
    public ProjectMember addMember(Long userId) {
        ProjectMember member = new ProjectMember(this, userId);
        members.add(member);
        return member;
    }
}
