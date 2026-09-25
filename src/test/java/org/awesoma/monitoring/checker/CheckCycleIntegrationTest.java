package org.awesoma.monitoring.checker;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.awesoma.monitoring.domain.entity.Channel;
import org.awesoma.monitoring.domain.entity.Incident;
import org.awesoma.monitoring.domain.entity.Monitor;
import org.awesoma.monitoring.domain.entity.Project;
import org.awesoma.monitoring.domain.entity.User;
import org.awesoma.monitoring.domain.enums.ChannelType;
import org.awesoma.monitoring.domain.enums.IncidentStatus;
import org.awesoma.monitoring.domain.enums.MemberRole;
import org.awesoma.monitoring.domain.enums.MonitorState;
import org.awesoma.monitoring.domain.model.MonitorTarget;
import org.awesoma.monitoring.domain.model.ProbeOutcome;
import org.awesoma.monitoring.repository.CheckResultRepository;
import org.awesoma.monitoring.repository.IncidentRepository;
import org.awesoma.monitoring.repository.NotificationRepository;
import org.awesoma.monitoring.service.MonitorCheckService;
import org.awesoma.monitoring.service.MonitorService;
import org.awesoma.monitoring.web.dto.monitor.MonitorUpdateRequest;
import org.awesoma.monitoring.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

/** The failure-to-recovery cycle against a real database, indexes and constraints included. */
@Transactional
class CheckCycleIntegrationTest extends AbstractIntegrationTest {

    @Autowired private EntityManager entityManager;
    @Autowired private MonitorCheckService checks;
    @Autowired private IncidentRepository incidents;
    @Autowired private NotificationRepository notifications;
    @Autowired private CheckResultRepository checkResults;
    @Autowired private MonitorService monitorService;

    @Test
    void aFailureOpensOneIncidentAndARecoveryClosesIt() {
        Monitor monitor = seed("cycle");
        entityManager.flush();

        checks.record(monitor.getId(), ProbeOutcome.connectionError(12, "connection refused"));
        entityManager.flush();

        assertThat(monitor.getCurrentState()).isEqualTo(MonitorState.DOWN);
        Incident incident = incidents
                .findByMonitorIdAndStatus(monitor.getId(), IncidentStatus.OPEN)
                .orElseThrow();
        assertThat(incident.getCause()).isEqualTo("connection refused");
        // Only the enabled channel is notified; the disabled one is skipped.
        assertThat(notifications.findByIncidentId(incident.getId(), Pageable.unpaged()))
                .hasSize(1);

        // A monitor that is already down must not accumulate a second incident.
        checks.record(monitor.getId(), ProbeOutcome.connectionError(9, "connection refused"));
        entityManager.flush();
        assertThat(incidents.findByMonitorId(monitor.getId(), Pageable.unpaged())).hasSize(1);

        checks.record(monitor.getId(), ProbeOutcome.success(85, 200));
        entityManager.flush();

        assertThat(monitor.getCurrentState()).isEqualTo(MonitorState.UP);
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(incident.getResolvedAt()).isNotNull();
        assertThat(notifications.findByIncidentId(incident.getId(), Pageable.unpaged()))
                .as("recovery is announced as well as the failure")
                .hasSize(2);
        assertThat(checkResults.findByMonitorIdOrderByCheckedAtDesc(monitor.getId(), Pageable.unpaged()))
                .hasSize(3);
    }

    @Test
    void aMonitorPausedWhileDownKeepsItsIncidentWhenItFailsAgain() {
        Monitor monitor = seed("paused-failing");
        entityManager.flush();
        checks.record(monitor.getId(), ProbeOutcome.connectionError(12, "connection refused"));
        entityManager.flush();

        pauseAndResume(monitor);
        checks.record(monitor.getId(), ProbeOutcome.connectionError(9, "connection refused"));
        entityManager.flush();

        assertThat(monitor.getCurrentState()).isEqualTo(MonitorState.DOWN);
        assertThat(incidents.findByMonitorId(monitor.getId(), Pageable.unpaged()))
                .as("the incident left open by the pause is reused, not duplicated")
                .hasSize(1);
        assertThat(checkResults.findByMonitorIdOrderByCheckedAtDesc(monitor.getId(), Pageable.unpaged()))
                .hasSize(2);
    }

    @Test
    void aMonitorPausedWhileDownClosesItsIncidentWhenItRecovers() {
        Monitor monitor = seed("paused-recovering");
        entityManager.flush();
        checks.record(monitor.getId(), ProbeOutcome.connectionError(12, "connection refused"));
        entityManager.flush();
        Incident incident = incidents
                .findByMonitorIdAndStatus(monitor.getId(), IncidentStatus.OPEN)
                .orElseThrow();

        pauseAndResume(monitor);
        checks.record(monitor.getId(), ProbeOutcome.success(85, 200));
        entityManager.flush();

        assertThat(monitor.getCurrentState()).isEqualTo(MonitorState.UP);
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(notifications.findByIncidentId(incident.getId(), Pageable.unpaged())).hasSize(2);
    }

    @Test
    void aMonitorIsDueUntilItHasBeenChecked() {
        Monitor monitor = seed("due");
        entityManager.flush();

        List<MonitorTarget> due = checks.findDueTargets(10);
        assertThat(due).extracting(MonitorTarget::monitorId).contains(monitor.getId());

        checks.record(monitor.getId(), ProbeOutcome.success(40, 200));
        entityManager.flush();

        assertThat(checks.findDueTargets(10))
                .as("the interval has not elapsed yet")
                .extracting(MonitorTarget::monitorId)
                .doesNotContain(monitor.getId());
    }

    @Test
    void anInactiveMonitorIsNeverDue() {
        Monitor monitor = seed("inactive");
        monitor.setActive(false);
        entityManager.flush();

        assertThat(checks.findDueTargets(10))
                .extracting(MonitorTarget::monitorId)
                .doesNotContain(monitor.getId());
    }

    private void pauseAndResume(Monitor monitor) {
        for (boolean active : new boolean[] {false, true}) {
            monitorService.update(monitor.getId(), new MonitorUpdateRequest(
                    monitor.getName(),
                    monitor.getUrl(),
                    monitor.getHttpMethod(),
                    monitor.getIntervalSec(),
                    monitor.getTimeoutMs(),
                    monitor.getExpectedStatus(),
                    active));
        }
        entityManager.flush();
        assertThat(monitor.getCurrentState()).isEqualTo(MonitorState.UNKNOWN);
    }

    private Monitor seed(String slug) {
        User owner = new User();
        owner.setEmail(slug + "@example.com");
        owner.setPassword("secret123");
        owner.setFullName("Owner");
        entityManager.persist(owner);

        Project project = new Project();
        project.setOwner(owner);
        project.setName("Project");
        project.setSlug(slug);
        project.addMember(owner, MemberRole.OWNER);
        entityManager.persist(project);

        Channel enabled = new Channel();
        enabled.setProject(project);
        enabled.setType(ChannelType.EMAIL);
        enabled.setTarget("ops@example.com");
        entityManager.persist(enabled);

        Channel disabled = new Channel();
        disabled.setProject(project);
        disabled.setType(ChannelType.WEBHOOK);
        disabled.setTarget("https://hooks.example.com/" + slug);
        disabled.setEnabled(false);
        entityManager.persist(disabled);

        Monitor monitor = new Monitor();
        monitor.setProject(project);
        monitor.setName("Monitor");
        monitor.setUrl("https://example.com");
        monitor.setIntervalSec(60);
        monitor.setTimeoutMs(5000);
        entityManager.persist(monitor);
        return monitor;
    }
}
