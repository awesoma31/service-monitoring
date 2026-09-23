package org.awesoma.monitoring.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import org.awesoma.monitoring.domain.entity.CheckResult;
import org.awesoma.monitoring.domain.entity.Monitor;
import org.awesoma.monitoring.domain.entity.Project;
import org.awesoma.monitoring.domain.entity.User;
import org.awesoma.monitoring.domain.enums.CheckResultType;
import org.awesoma.monitoring.domain.enums.MemberRole;
import org.awesoma.monitoring.service.IncidentService;
import org.awesoma.monitoring.support.AbstractIntegrationTest;
import org.awesoma.monitoring.web.dto.incident.CheckResultResponse;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class CheckHistoryQueryCountTest extends AbstractIntegrationTest {

    @Autowired private EntityManager entityManager;
    @Autowired private IncidentService incidents;

    @Test
    void scrollingTheHistoryNeverCountsTheRows() {
        Monitor monitor = seedMonitorWithChecks(7);
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
        statistics.clear();

        Slice<CheckResultResponse> slice =
                incidents.listResults(monitor.getId(), PageRequest.of(0, 3));

        assertThat(slice.getContent()).hasSize(3);
        assertThat(slice.hasNext()).isTrue();
        assertThat(statistics.getPrepareStatementCount())
                .as("a slice must not pay for counting rows it never reports")
                .isLessThanOrEqualTo(2);
    }

    private Monitor seedMonitorWithChecks(int count) {
        User owner = new User();
        owner.setEmail("slice-count@example.com");
        owner.setPasswordHash("hash");
        owner.setFullName("Owner");
        entityManager.persist(owner);

        Project project = new Project();
        project.setOwner(owner);
        project.setName("Project");
        project.setSlug("slice-count");
        project.addMember(owner, MemberRole.OWNER);
        entityManager.persist(project);

        Monitor monitor = new Monitor();
        monitor.setProject(project);
        monitor.setName("Monitor");
        monitor.setUrl("https://example.com");
        monitor.setIntervalSec(60);
        monitor.setTimeoutMs(5000);
        entityManager.persist(monitor);

        for (int index = 0; index < count; index++) {
            CheckResult result = new CheckResult();
            result.setMonitor(monitor);
            result.setCheckedAt(OffsetDateTime.now().minusMinutes(index));
            result.setResult(CheckResultType.SUCCESS);
            result.setResponseMs(10 + index);
            result.setHttpStatus(200);
            entityManager.persist(result);
        }
        return monitor;
    }
}
