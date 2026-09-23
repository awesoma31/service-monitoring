package org.awesoma.monitoring.domain;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import org.awesoma.monitoring.domain.entity.Channel;
import org.awesoma.monitoring.domain.entity.CheckResult;
import org.awesoma.monitoring.domain.entity.Incident;
import org.awesoma.monitoring.domain.entity.Monitor;
import org.awesoma.monitoring.domain.entity.Notification;
import org.awesoma.monitoring.domain.entity.Project;
import org.awesoma.monitoring.domain.entity.Tag;
import org.awesoma.monitoring.domain.entity.User;
import org.awesoma.monitoring.domain.enums.ChannelType;
import org.awesoma.monitoring.domain.enums.CheckResultType;
import org.awesoma.monitoring.domain.enums.MemberRole;
import org.awesoma.monitoring.domain.enums.Severity;
import org.awesoma.monitoring.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class EntityMappingTest extends AbstractIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsAndReloadsTheWholeGraph() {
        User owner = owner("graph@example.com");
        Project project = project(owner, "graph");
        Tag tag = tag("production");
        Monitor monitor = monitor(project, tag);

        CheckResult check = new CheckResult();
        check.setMonitor(monitor);
        check.setCheckedAt(OffsetDateTime.now());
        check.setResult(CheckResultType.CONNECTION_ERROR);
        check.setErrorMessage("connection refused");
        entityManager.persist(check);

        Incident incident = new Incident();
        incident.setMonitor(monitor);
        incident.setStartedAt(OffsetDateTime.now());
        incident.setSeverity(Severity.HIGH);
        entityManager.persist(incident);

        Channel channel = new Channel();
        channel.setProject(project);
        channel.setType(ChannelType.EMAIL);
        channel.setTarget("ops@example.com");
        entityManager.persist(channel);

        Notification notification = new Notification();
        notification.setIncident(incident);
        notification.setChannel(channel);
        entityManager.persist(notification);

        entityManager.flush();
        entityManager.clear();

        Monitor reloaded = entityManager.find(Monitor.class, monitor.getId());
        assertThat(reloaded.getTags()).extracting(Tag::getName).containsExactly("production");
        assertThat(reloaded.getProject().getName()).isEqualTo("Graph");

        Project reloadedProject = entityManager.find(Project.class, project.getId());
        assertThat(reloadedProject.getCreatedAt()).isNotNull();
        assertThat(reloadedProject.getMembers()).singleElement().satisfies(member -> {
            assertThat(member.getRole()).isEqualTo(MemberRole.OWNER);
            assertThat(member.getJoinedAt()).isNotNull();
            assertThat(member.getUser().getId()).isEqualTo(owner.getId());
        });

        Notification reloadedNotification =
                entityManager.find(Notification.class, notification.getId());
        assertThat(reloadedNotification.getIncident().getId()).isEqualTo(incident.getId());
        assertThat(reloadedNotification.getChannel().getId()).isEqualTo(channel.getId());
    }

    @Test
    void storesEnumsAsStrings() {
        User user = owner("enums@example.com");
        Project project = project(user, "enums");
        Monitor monitor = monitor(project, tag("enum-tag"));
        entityManager.flush();

        assertThat(column("SELECT status FROM users WHERE id = ?", user.getId()))
                .isEqualTo("ACTIVE");
        assertThat(column("SELECT current_state FROM monitors WHERE id = ?", monitor.getId()))
                .isEqualTo("UNKNOWN");
        assertThat(column("SELECT http_method FROM monitors WHERE id = ?", monitor.getId()))
                .isEqualTo("GET");
        assertThat(column("SELECT role FROM project_members WHERE project_id = ?", project.getId()))
                .isEqualTo("OWNER");
    }

    @Test
    void resolvingAnIncidentSetsStatusAndTimestampTogether() {
        Monitor monitor = monitor(project(owner("resolve@example.com"), "resolve"), tag("t"));
        Incident incident = new Incident();
        incident.setMonitor(monitor);
        incident.setStartedAt(OffsetDateTime.now());
        incident.setSeverity(Severity.LOW);
        entityManager.persist(incident);

        incident.resolve(OffsetDateTime.now());
        entityManager.flush();

        assertThat(incident.isOpen()).isFalse();
        assertThat(incident.getResolvedAt()).isNotNull();
    }

    private String column(String sql, Long id) {
        return (String) entityManager.createNativeQuery(sql.replace("?", "?1"))
                .setParameter(1, id)
                .getSingleResult();
    }

    private User owner(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setFullName("Owner");
        entityManager.persist(user);
        return user;
    }

    private Project project(User owner, String slug) {
        Project project = new Project();
        project.setOwner(owner);
        project.setName("Graph");
        project.setSlug(slug);
        project.addMember(owner, MemberRole.OWNER);
        entityManager.persist(project);
        return project;
    }

    private Tag tag(String name) {
        Tag tag = new Tag();
        tag.setName(name);
        entityManager.persist(tag);
        return tag;
    }

    private Monitor monitor(Project project, Tag tag) {
        Monitor monitor = new Monitor();
        monitor.setProject(project);
        monitor.setName("Home page");
        monitor.setUrl("https://example.com");
        monitor.setIntervalSec(60);
        monitor.setTimeoutMs(5000);
        monitor.getTags().add(tag);
        entityManager.persist(monitor);
        return monitor;
    }
}
