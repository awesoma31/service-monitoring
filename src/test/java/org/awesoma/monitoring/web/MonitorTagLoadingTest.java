package org.awesoma.monitoring.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.Set;
import org.awesoma.monitoring.domain.entity.Monitor;
import org.awesoma.monitoring.domain.entity.Project;
import org.awesoma.monitoring.domain.entity.Tag;
import org.awesoma.monitoring.domain.entity.User;
import org.awesoma.monitoring.domain.enums.MemberRole;
import org.awesoma.monitoring.service.MonitorService;
import org.awesoma.monitoring.support.AbstractIntegrationTest;
import org.awesoma.monitoring.web.dto.monitor.MonitorResponse;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class MonitorTagLoadingTest extends AbstractIntegrationTest {

    private static final int MONITOR_COUNT = 5;
    private static final int PAGE_SIZE = 3;

    @Autowired private EntityManager entityManager;
    @Autowired private MonitorService monitors;

    @Test
    void aPageOfMonitorsLoadsEveryTagInOneExtraQuery() {
        Project project = seedProjectWithTaggedMonitors();
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
        statistics.clear();

        Page<MonitorResponse> page =
                monitors.listByProject(project.getId(), null, PageRequest.of(0, PAGE_SIZE));
        page.getContent().forEach(monitor -> assertThat(monitor.tags()).isNotEmpty());

        assertThat(page.getContent()).hasSize(PAGE_SIZE);
        assertThat(page.getTotalElements()).isEqualTo(MONITOR_COUNT);
        assertThat(statistics.getPrepareStatementCount())
                .as("a page of %d monitors must not cost a query per row", PAGE_SIZE)
                .isLessThanOrEqualTo(4);
    }

    private Project seedProjectWithTaggedMonitors() {
        User owner = new User();
        owner.setEmail("tag-loading@example.com");
        owner.setPasswordHash("hash");
        owner.setFullName("Owner");
        entityManager.persist(owner);

        Project project = new Project();
        project.setOwner(owner);
        project.setName("Tag loading");
        project.setSlug("tag-loading");
        project.addMember(owner, MemberRole.OWNER);
        entityManager.persist(project);

        for (int index = 0; index < MONITOR_COUNT; index++) {
            Tag tag = new Tag();
            tag.setName("tag-" + index);
            entityManager.persist(tag);

            Monitor monitor = new Monitor();
            monitor.setProject(project);
            monitor.setName("Monitor " + index);
            monitor.setUrl("https://example.com/" + index);
            monitor.setIntervalSec(60);
            monitor.setTimeoutMs(5000);
            monitor.setTags(Set.of(tag));
            entityManager.persist(monitor);
        }
        return project;
    }
}
