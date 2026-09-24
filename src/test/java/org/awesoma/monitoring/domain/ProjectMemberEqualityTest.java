package org.awesoma.monitoring.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.awesoma.monitoring.domain.entity.Project;
import org.awesoma.monitoring.domain.entity.ProjectMember;
import org.awesoma.monitoring.domain.entity.ProjectMemberId;
import org.awesoma.monitoring.domain.entity.User;
import org.awesoma.monitoring.domain.enums.MemberRole;
import org.junit.jupiter.api.Test;

class ProjectMemberEqualityTest {

    @Test
    void keysAreEqualWhenBothColumnsMatch() {
        assertThat(new ProjectMemberId(1L, 2L))
                .isEqualTo(new ProjectMemberId(1L, 2L))
                .hasSameHashCodeAs(new ProjectMemberId(1L, 2L));
    }

    @Test
    void keysDifferWhenEitherColumnDiffers() {
        ProjectMemberId key = new ProjectMemberId(1L, 2L);

        assertThat(key)
                .isNotEqualTo(new ProjectMemberId(1L, 3L))
                .isNotEqualTo(new ProjectMemberId(3L, 2L))
                .isNotEqualTo("not a key")
                .isEqualTo(key);
    }

    @Test
    void membersAreComparedByTheirKey() {
        ProjectMember member = member(1L, 2L);
        ProjectMember same = member(1L, 2L);
        ProjectMember other = member(1L, 9L);

        assertThat(member)
                .isEqualTo(same)
                .hasSameHashCodeAs(same)
                .isNotEqualTo(other)
                .isNotEqualTo("not a member");
    }

    private ProjectMember member(Long projectId, Long userId) {
        Project project = new Project();
        project.setId(projectId);
        User user = new User();
        user.setId(userId);

        ProjectMember member = new ProjectMember(project, user, MemberRole.VIEWER);
        member.setId(new ProjectMemberId(projectId, userId));
        return member;
    }
}
