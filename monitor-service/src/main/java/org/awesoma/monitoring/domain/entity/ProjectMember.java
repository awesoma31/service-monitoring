package org.awesoma.monitoring.domain.entity;

import java.time.OffsetDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Membership of a user in a project — a many-to-many association that carries its own
 * data ({@code joinedAt}), which is why it is an entity rather than a join table.
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ProjectMember {

    @EqualsAndHashCode.Include
    private ProjectMemberId id = new ProjectMemberId();

    private Project project;


    private OffsetDateTime joinedAt;

    public ProjectMember(Project project, Long userId) {
        this.project = project;
        this.id = new ProjectMemberId(project.getId(), userId);
    }

    public Long getUserId() { return id.getUserId(); }

}
