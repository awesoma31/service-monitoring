package org.awesoma.monitoring.domain;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import org.awesoma.monitoring.domain.entity.Incident;
import org.awesoma.monitoring.domain.entity.Monitor;
import org.awesoma.monitoring.domain.entity.Project;
import org.awesoma.monitoring.domain.entity.Tag;
import org.awesoma.monitoring.domain.entity.User;
import org.awesoma.monitoring.domain.enums.Severity;
import org.awesoma.monitoring.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Booting at all already proves the mapping matches the migrated schema, since Hibernate
 * validates it. These tests go further and exercise every association type through a
 * round trip.
 */
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

        Incident incident = new Incident();
        incident.setMonitor(monitor);
        incident.setStartedAt(OffsetDateTime.now());
        incident.setSeverity(Severity.HIGH);
        entityManager.persist(incident);


        entityManager.flush();
        entityManager.clear();

        Monitor reloaded = entityManager.find(Monitor.class, monitor.getId());
        assertThat(reloaded.getTags()).extracting(Tag::getName).containsExactly("production");
        assertThat(reloaded.getProject().getName()).isEqualTo("Graph");

        Project reloadedProject = entityManager.find(Project.class, project.getId());
        assertThat(reloadedProject.getCreatedAt()).isNotNull();
        assertThat(reloadedProject.getMembers()).singleElement().satisfies(member -> {
            assertThat(member.getJoinedAt()).isNotNull();
            assertThat(member.getUser().getId()).isEqualTo(owner.getId());
        });

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
        user.setPassword("secret123");
        user.setFullName("Owner");
        entityManager.persist(user);
        return user;
    }

    private Project project(User owner, String slug) {
        Project project = new Project();
        project.setOwner(owner);
        project.setName("Graph");
        project.setSlug(slug);
        project.addMember(owner);
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
