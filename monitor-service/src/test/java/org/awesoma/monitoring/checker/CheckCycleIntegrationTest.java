package org.awesoma.monitoring.checker;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.awesoma.monitoring.domain.entity.Incident;
import org.awesoma.monitoring.domain.entity.Monitor;
import org.awesoma.monitoring.domain.entity.Project;
import org.awesoma.monitoring.domain.entity.User;
import org.awesoma.monitoring.domain.enums.IncidentStatus;
import org.awesoma.monitoring.domain.enums.MonitorState;
import org.awesoma.monitoring.domain.model.MonitorTarget;
import org.awesoma.monitoring.domain.model.ProbeOutcome;
import org.awesoma.monitoring.repository.IncidentRepository;
import org.awesoma.monitoring.service.MonitorCheckService;
import org.awesoma.monitoring.service.MonitorService;
import org.awesoma.monitoring.web.dto.monitor.MonitorUpdateRequest;
import org.awesoma.monitoring.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.awesoma.monitoring.integration.IncidentChanged;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/** The failure-to-recovery cycle against a real database, indexes and constraints included. */
@Transactional
@RecordApplicationEvents
class CheckCycleIntegrationTest extends AbstractIntegrationTest {

    @Autowired private EntityManager entityManager;
    @Autowired private MonitorCheckService checks;
    @Autowired private IncidentRepository incidents;
    @Autowired private ApplicationEvents events;
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
        assertThat(announced()).containsExactly(IncidentChanged.Kind.OPENED);

        // A monitor that is already down must not accumulate a second incident.
        checks.record(monitor.getId(), ProbeOutcome.connectionError(9, "connection refused"));
        entityManager.flush();
        assertThat(incidents.findByMonitorId(monitor.getId(), Pageable.unpaged())).hasSize(1);

        checks.record(monitor.getId(), ProbeOutcome.success(85, 200));
        entityManager.flush();

        assertThat(monitor.getCurrentState()).isEqualTo(MonitorState.UP);
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(incident.getResolvedAt()).isNotNull();
        assertThat(announced())
                .as("recovery is announced as well as the failure, each exactly once")
                .containsExactly(IncidentChanged.Kind.OPENED, IncidentChanged.Kind.RESOLVED);
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
        assertThat(announced())
                .containsExactly(IncidentChanged.Kind.OPENED, IncidentChanged.Kind.RESOLVED);
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

    private java.util.List<IncidentChanged.Kind> announced() {
        return events.stream(IncidentChanged.class).map(IncidentChanged::kind).toList();
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
        project.addMember(owner);
        entityManager.persist(project);

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
