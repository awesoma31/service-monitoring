package org.awesoma.monitoring.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import org.awesoma.monitoring.domain.entity.Channel;
import org.awesoma.monitoring.domain.entity.Incident;
import org.awesoma.monitoring.domain.entity.Monitor;
import org.awesoma.monitoring.domain.entity.Project;
import org.awesoma.monitoring.domain.entity.User;
import org.awesoma.monitoring.domain.enums.ChannelType;
import org.awesoma.monitoring.domain.enums.IncidentStatus;
import org.awesoma.monitoring.domain.enums.MemberRole;
import org.awesoma.monitoring.domain.enums.MonitorState;
import org.awesoma.monitoring.domain.model.ProbeOutcome;
import org.awesoma.monitoring.repository.IncidentRepository;
import org.awesoma.monitoring.service.MonitorCheckService;
import org.awesoma.monitoring.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class IncidentApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager entityManager;
    @Autowired private MonitorCheckService checks;
    @Autowired private IncidentRepository incidents;

    @Test
    void checkHistoryScrollsWithoutEverCountingTheRows() throws Exception {
        Monitor monitor = seed("history");
        record(monitor, ProbeOutcome.success(10, 200));
        record(monitor, ProbeOutcome.connectionError(5, "refused"));
        record(monitor, ProbeOutcome.success(12, 200));

        mockMvc.perform(get("/api/v1/monitors/{id}/results", monitor.getId()).param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.last").value(false))
                // A slice reports whether more rows follow, never how many exist.
                .andExpect(jsonPath("$.totalElements").doesNotExist())
                .andExpect(jsonPath("$.totalPages").doesNotExist());

        mockMvc.perform(get("/api/v1/monitors/{id}/results", monitor.getId())
                        .param("size", "2")
                        .param("page", "1"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void newestCheckComesFirst() throws Exception {
        Monitor monitor = seed("ordering");
        record(monitor, ProbeOutcome.success(10, 200));
        record(monitor, ProbeOutcome.connectionError(5, "refused"));

        mockMvc.perform(get("/api/v1/monitors/{id}/results", monitor.getId()))
                .andExpect(jsonPath("$.content[0].result").value("CONNECTION_ERROR"))
                .andExpect(jsonPath("$.content[1].result").value("SUCCESS"));
    }

    @Test
    void incidentsCanBeFilteredByStatus() throws Exception {
        Monitor monitor = seed("filter");
        record(monitor, ProbeOutcome.connectionError(5, "refused"));
        record(monitor, ProbeOutcome.success(10, 200));

        mockMvc.perform(get("/api/v1/monitors/{id}/incidents", monitor.getId()))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/v1/monitors/{id}/incidents", monitor.getId())
                        .param("status", "RESOLVED"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/v1/monitors/{id}/incidents", monitor.getId())
                        .param("status", "OPEN"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void anIncidentCanBeResolvedByHandOnlyOnce() throws Exception {
        Monitor monitor = seed("manual");
        record(monitor, ProbeOutcome.connectionError(5, "refused"));
        Incident incident = incidents
                .findByMonitorIdAndStatus(monitor.getId(), IncidentStatus.OPEN)
                .orElseThrow();

        mockMvc.perform(post("/api/v1/incidents/{id}/resolve", incident.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").exists());

        // The monitor stops claiming to be DOWN, so a later failure opens a fresh incident.
        assertThat(monitor.getCurrentState()).isEqualTo(MonitorState.UNKNOWN);

        mockMvc.perform(post("/api/v1/incidents/{id}/resolve", incident.getId()))
                .andExpect(status().isConflict());
    }

    @Test
    void notificationsOfAnIncidentAreListed() throws Exception {
        Monitor monitor = seed("notified");
        record(monitor, ProbeOutcome.timeout(5000, "timed out"));
        Incident incident = incidents
                .findByMonitorIdAndStatus(monitor.getId(), IncidentStatus.OPEN)
                .orElseThrow();

        mockMvc.perform(get("/api/v1/incidents/{id}/notifications", incident.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.content[0].target").value("ops@example.com"));
    }

    @Test
    void unknownIncidentAndUnknownMonitorAreNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/incidents/{id}", 999_999)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/monitors/{id}/results", 999_999)).andExpect(status().isNotFound());
    }

    private void record(Monitor monitor, ProbeOutcome outcome) {
        checks.record(monitor.getId(), outcome);
        entityManager.flush();
    }

    private Monitor seed(String slug) {
        User owner = new User();
        owner.setEmail(slug + "@example.com");
        owner.setPasswordHash("hash");
        owner.setFullName("Owner");
        entityManager.persist(owner);

        Project project = new Project();
        project.setOwner(owner);
        project.setName("Project");
        project.setSlug(slug);
        project.addMember(owner, MemberRole.OWNER);
        entityManager.persist(project);

        Channel channel = new Channel();
        channel.setProject(project);
        channel.setType(ChannelType.EMAIL);
        channel.setTarget("ops@example.com");
        entityManager.persist(channel);

        Monitor monitor = new Monitor();
        monitor.setProject(project);
        monitor.setName("Monitor");
        monitor.setUrl("https://example.com");
        monitor.setIntervalSec(60);
        monitor.setTimeoutMs(5000);
        entityManager.persist(monitor);
        entityManager.flush();
        return monitor;
    }
}
