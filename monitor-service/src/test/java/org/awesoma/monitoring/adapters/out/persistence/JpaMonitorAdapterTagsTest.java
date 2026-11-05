package org.awesoma.monitoring.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.awesoma.monitoring.adapters.out.persistence.entity.Monitor;
import org.awesoma.monitoring.adapters.out.persistence.entity.Project;
import org.awesoma.monitoring.adapters.out.persistence.entity.Tag;
import org.awesoma.monitoring.support.AbstractIntegrationTest;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * A monitor is saved on every claim and every check result, and those saves do not touch its
 * tags. They must leave the join table alone instead of rewriting the monitor's rows in it.
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class JpaMonitorAdapterTagsTest extends AbstractIntegrationTest {

    @Autowired private EntityManager entityManager;
    @Autowired private JpaMonitorAdapter adapter;

    @Test
    void savingWithTheSameTagsDoesNotWriteTheJoinTable() {
        Long monitorId = seedMonitor(tag("adapter-kept"), tag("adapter-other"));
        Statistics statistics = statistics();

        var monitor = adapter.findById(monitorId).orElseThrow();
        monitor.setLastCheckedAt(OffsetDateTime.now());
        adapter.save(monitor);
        entityManager.flush();

        assertThat(statistics.getCollectionRemoveCount()).isZero();
        assertThat(statistics.getCollectionRecreateCount()).isZero();
        assertThat(statistics.getCollectionUpdateCount()).isZero();
    }

    @Test
    void savingWithOtherTagsUpdatesTheJoinTableInPlace() {
        Tag kept = tag("adapter-kept");
        Tag dropped = tag("adapter-dropped");
        Tag added = tag("adapter-added");
        Long monitorId = seedMonitor(kept, dropped);
        Statistics statistics = statistics();

        var monitor = adapter.findById(monitorId).orElseThrow();
        monitor.setTags(new LinkedHashSet<>(Set.of(PersistenceMapping.tag(kept), PersistenceMapping.tag(added))));
        adapter.save(monitor);
        entityManager.flush();

        assertThat(statistics.getCollectionRemoveCount()).isZero();
        assertThat(statistics.getCollectionRecreateCount()).isZero();
        assertThat(statistics.getCollectionUpdateCount()).isEqualTo(1);

        entityManager.clear();
        assertThat(entityManager.find(Monitor.class, monitorId).getTags())
                .extracting(Tag::getName)
                .containsExactlyInAnyOrder("adapter-kept", "adapter-added");
    }

    private Tag tag(String name) {
        Tag tag = new Tag();
        tag.setName(name);
        entityManager.persist(tag);
        return tag;
    }

    private Long seedMonitor(Tag... tags) {
        Long owner = remoteUser("adapter-tags@example.com");

        Project project = new Project();
        project.setOwnerId(owner);
        project.setName("Adapter tags");
        project.setSlug("adapter-tags");
        project.addMember(owner);
        entityManager.persist(project);

        Monitor monitor = new Monitor();
        monitor.setProject(project);
        monitor.setName("Monitor");
        monitor.setUrl("https://example.com");
        monitor.setIntervalSec(60);
        monitor.setTimeoutMs(5000);
        monitor.getTags().addAll(List.of(tags));
        entityManager.persist(monitor);

        entityManager.flush();
        entityManager.clear();
        return monitor.getId();
    }

    private Statistics statistics() {
        Statistics statistics = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
        statistics.clear();
        return statistics;
    }
}
