package org.awesoma.monitoring.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awesoma.monitoring.adapters.out.persistence.entity.Incident;
import org.awesoma.monitoring.adapters.out.persistence.entity.Monitor;
import org.awesoma.monitoring.adapters.out.persistence.entity.Project;
import org.awesoma.monitoring.domain.enums.IncidentStatus;
import org.awesoma.monitoring.domain.enums.Severity;
import org.awesoma.monitoring.support.AbstractIntegrationTest;
import org.awesoma.security.jwt.JwtTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@WithAnonymousUser
@Transactional
class InternalReportDataApiIntegrationTest extends AbstractIntegrationTest {
    private static final OffsetDateTime FROM = OffsetDateTime.parse("2026-10-01T00:00:00Z");
    private static final OffsetDateTime TO = OffsetDateTime.parse("2026-10-02T00:00:00Z");

    @Autowired private MockMvc http;
    @Autowired private EntityManager entities;
    @Autowired private JwtTokens tokens;
    @Autowired private ObjectMapper json;

    private Project project;
    private Project other;
    private List<Monitor> monitors;
    private Monitor foreign;

    @BeforeEach
    void fixtures() {
        project = project("Сервисы");
        other = project("Other");
        monitors = new ArrayList<>(List.of(monitor(project, "a"), monitor(project, "b"), monitor(project, "c")));
        foreign = monitor(other, "x");
        entities.flush();
    }

    @Test
    void walksEveryMonitorOfTheProjectByIdUpToTheBoundOfTheFirstPage() throws Exception {
        JsonNode first = read(call("/selection", Map.of("limit", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.project.id").value(project.getId()))
                .andExpect(jsonPath("$.project.name").value("Сервисы"))
                .andExpect(jsonPath("$.items[0].id").value(monitors.get(0).getId()))
                .andExpect(jsonPath("$.items[0].url").value("https://a.example.com"))
                .andExpect(jsonPath("$.items[1].id").value(monitors.get(1).getId()))
                .andExpect(jsonPath("$.has_next").value(true)));
        monitor(project, "late");
        entities.flush();

        call("/selection", Map.of("limit", 2, "cursor", first.get("next_cursor").asText()))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(monitors.get(2).getId()))
                .andExpect(jsonPath("$.has_next").value(false))
                .andExpect(jsonPath("$.next_cursor").isEmpty())
                .andExpect(jsonPath("$.upper_id").value(first.get("upper_id").asLong()))
                .andExpect(jsonPath("$.source_captured_at").value(first.get("source_captured_at").asText()));
    }

    @Test
    void selectsOnlyTheListedMonitorsAndRefusesForeignOnes() throws Exception {
        call("/selection", Map.of("limit", 50, "monitor_ids", List.of(monitors.get(2).getId(), monitors.get(0).getId())))
                .andExpect(jsonPath("$.items[*].id").value(contains(
                        monitors.get(0).getId().intValue(), monitors.get(2).getId().intValue())));
        call("/selection", Map.of("limit", 50, "monitor_ids", List.of(monitors.get(0).getId(), foreign.getId())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MONITOR_NOT_IN_PROJECT"));
    }

    @Test
    void answersOwnershipWithoutFailing() throws Exception {
        call("/monitors/ownership", Map.of("monitor_ids", List.of(monitors.get(0).getId(), monitors.get(0).getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ownership").value("OWNED"));
        call("/monitors/ownership", Map.of("monitor_ids", List.of(foreign.getId())))
                .andExpect(jsonPath("$.ownership").value("MONITOR_NOT_IN_PROJECT"));
        http.perform(service(post("/internal/report-data/projects/{id}/monitors/ownership", 999_999),
                        Map.of("monitor_ids", List.of(1))))
                .andExpect(jsonPath("$.ownership").value("PROJECT_NOT_FOUND"));
    }

    @Test
    void returnsIncidentsWhoseIntervalOverlapsThePeriod() throws Exception {
        var a = monitors.get(0);
        var b = monitors.get(1);
        var c = monitors.get(2);
        var openBefore = incident(a, FROM.minusDays(1), null, Severity.CRITICAL);
        var closedAtFrom = incident(b, FROM.minusHours(5), FROM, Severity.LOW);
        var closedInside = incident(b, FROM.minusHours(1), FROM.plusHours(1), Severity.MEDIUM);
        var startedAtTo = incident(c, TO, TO.plusHours(1), Severity.HIGH);
        var closedAfter = incident(c, TO.minusHours(1), TO.plusHours(1), Severity.HIGH);
        incident(foreign, FROM.plusHours(1), null, Severity.LOW);
        entities.flush();

        var body = Map.of("monitor_ids", List.of(a.getId(), b.getId(), c.getId()), "from", FROM.toInstant().toString(),
                "to", TO.toInstant().toString(), "limit", 50);
        JsonNode page = read(call("/incidents/search", body).andExpect(status().isOk()));

        List<Long> ids = new ArrayList<>();
        page.get("items").forEach(item -> ids.add(item.get("id").asLong()));
        assertThat(ids).containsExactly(openBefore.getId(), closedInside.getId(), closedAfter.getId())
                .doesNotContain(closedAtFrom.getId(), startedAtTo.getId());
        assertThat(page.get("items").get(0).get("severity").asText()).isEqualTo("CRITICAL");
        assertThat(page.get("items").get(0).get("resolved_at").isNull()).isTrue();
        assertThat(page.get("items").get(1).get("monitor_id").asLong()).isEqualTo(b.getId());
    }

    @Test
    void pagesIncidentsWithACursorBoundToTheQuery() throws Exception {
        for (Monitor monitor : monitors) incident(monitor, FROM.plusHours(1), FROM.plusHours(2), Severity.LOW);
        entities.flush();
        var ids = monitors.stream().map(Monitor::getId).toList();
        var body = new HashMap<String, Object>(Map.of("monitor_ids", ids, "from", FROM.toInstant().toString(),
                "to", TO.toInstant().toString(), "limit", 2));

        JsonNode first = read(call("/incidents/search", body).andExpect(jsonPath("$.items.length()").value(2)));
        body.put("cursor", first.get("next_cursor").asText());
        call("/incidents/search", body).andExpect(jsonPath("$.items.length()").value(1));

        body.put("to", TO.plusSeconds(1).toInstant().toString());
        call("/incidents/search", body).andExpect(status().isBadRequest());
        call("/selection", Map.of("limit", 2, "cursor", first.get("next_cursor").asText()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidRequests() throws Exception {
        call("/selection", Map.of("limit", 51)).andExpect(status().isBadRequest());
        call("/selection", Map.of("limit", 10, "monitor_ids", List.of())).andExpect(status().isBadRequest());
        call("/incidents/search", Map.of("monitor_ids", List.of(monitors.get(0).getId()), "from", TO.toInstant().toString(),
                "to", FROM.toInstant().toString(), "limit", 10)).andExpect(status().isBadRequest());
        call("/incidents/search", Map.of("monitor_ids", List.of(foreign.getId()), "from", FROM.toInstant().toString(),
                "to", TO.toInstant().toString(), "limit", 10)).andExpect(status().isUnprocessableEntity());
        http.perform(service(post("/internal/report-data/projects/{id}/selection", 999_999), Map.of("limit", 10)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }

    @Test
    void isForServicesOnly() throws Exception {
        Long supervisor = remoteUser("admin-" + UUID.randomUUID() + "@example.com", "SUPERVISOR");
        http.perform(post("/internal/report-data/projects/{id}/selection", project.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"limit\":10}")
                        .header("Authorization",
                                "Bearer " + tokens.issue(supervisor.toString(), "SUPERVISOR").getTokenValue()))
                .andExpect(status().isForbidden());
        http.perform(post("/internal/report-data/projects/{id}/selection", project.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"limit\":10}"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions call(String path, Map<String, ?> body) throws Exception {
        return http.perform(service(post("/internal/report-data/projects/{id}" + path, project.getId()), body));
    }

    private MockHttpServletRequestBuilder service(
            MockHttpServletRequestBuilder request, Map<String, ?> body)
            throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))
                .header("Authorization", "Bearer " + tokens.issue("report-service", "SERVICE").getTokenValue());
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private Project project(String name) {
        Long owner = remoteUser(UUID.randomUUID() + "@example.com");
        Project created = new Project();
        created.setOwnerId(owner);
        created.setName(name);
        created.setSlug("report-data-" + UUID.randomUUID());
        created.addMember(owner);
        entities.persist(created);
        return created;
    }

    private Monitor monitor(Project owner, String name) {
        Monitor monitor = new Monitor();
        monitor.setProject(owner);
        monitor.setName(name);
        monitor.setUrl("https://" + name + ".example.com");
        monitor.setIntervalSec(60);
        monitor.setTimeoutMs(5000);
        entities.persist(monitor);
        return monitor;
    }

    private Incident incident(Monitor monitor, OffsetDateTime startedAt, OffsetDateTime resolvedAt, Severity severity) {
        Incident incident = new Incident();
        incident.setMonitor(monitor);
        incident.setStartedAt(startedAt);
        incident.setSeverity(severity);
        if (resolvedAt != null) {
            incident.setStatus(org.awesoma.monitoring.domain.enums.IncidentStatus.RESOLVED);
            incident.setResolvedAt(resolvedAt);
        }
        entities.persist(incident);
        return incident;
    }
}
