package org.awesoma.report.adapters.in.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.awesoma.integration.IntegrationEvent;
import org.awesoma.report.application.port.ObjectStorage;
import org.awesoma.report.application.port.ReportRepository;
import org.awesoma.report.application.usecase.ReportMaintenance;
import org.awesoma.report.domain.ObjectKeys;
import org.awesoma.report.domain.ReportStatus;
import org.awesoma.report.support.AbstractIntegrationTest;
import org.awesoma.security.jwt.JwtTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** ProjectDeletion over HTTP: the same use case and inbox the Kafka listener uses. */
@AutoConfigureMockMvc
class ProjectDeletionIntegrationTest extends AbstractIntegrationTest {
    private static final AtomicLong IDS = new AtomicLong(9000);

    @Autowired private MockMvc http;
    @Autowired private JwtTokens tokens;
    @Autowired private ObjectMapper json;
    @Autowired private ReportRepository reports;
    @Autowired private ReportMaintenance maintenance;
    @Autowired private ObjectStorage storage;

    @Test
    void aDeletedProjectLosesItsReportsAndTakesNoNewOnes() throws Exception {
        long project = IDS.incrementAndGet();
        long operator = IDS.incrementAndGet();
        grantWrite(project, operator);
        String id = json.readTree(upload(project, operator).andExpect(status().isCreated()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8)).get("id").asText();
        var event = projectDeleted(project);

        send(event).andExpect(status().isNoContent());
        send(event).andExpect(status().isNoContent());

        assertThat(reports.findById(UUID.fromString(id)).orElseThrow().status()).isEqualTo(ReportStatus.DELETING);
        user(get("/api/v1/reports/" + id), operator).andExpect(status().isNotFound());
        upload(project, operator).andExpect(status().isNotFound());

        maintenance.purgeDeleted(100);
        assertThat(reports.findById(UUID.fromString(id))).isEmpty();
        assertThat(storage.list(ObjectKeys.projectPrefix(project))).isEmpty();
    }

    @Test
    void acknowledgesAMonitorDeletionAndRefusesMalformedEventsAndUsers() throws Exception {
        send(new IntegrationEvent(UUID.randomUUID(), 1, IntegrationEvent.MONITOR_DELETED, OffsetDateTime.now(),
                5L, null, null, null, null, null)).andExpect(status().isNoContent());
        send(new IntegrationEvent(UUID.randomUUID(), 1, IntegrationEvent.PROJECT_DELETED, OffsetDateTime.now(),
                null, null, null, null, List.of(), null)).andExpect(status().isBadRequest());
        http.perform(post("/internal/integration/events").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(projectDeleted(1)))
                        .header("Authorization", "Bearer " + tokens.issue("1", "SUPERVISOR").getTokenValue()))
                .andExpect(status().isForbidden());
    }

    private static IntegrationEvent projectDeleted(long project) {
        return new IntegrationEvent(UUID.randomUUID(), 1, IntegrationEvent.PROJECT_DELETED, OffsetDateTime.now(),
                null, project, null, null, List.of(), null);
    }

    private ResultActions send(IntegrationEvent event) throws Exception {
        return http.perform(post("/internal/integration/events").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(event))
                .header("Authorization", "Bearer " + tokens.issue("monitor-service", "SERVICE").getTokenValue()));
    }

    private ResultActions upload(long project, long user) throws Exception {
        return user(multipart("/api/v1/projects/" + project + "/reports").file(new MockMultipartFile("file",
                "r.csv", "text/csv", "a,b\n".getBytes(StandardCharsets.UTF_8))), user);
    }

    private ResultActions user(MockHttpServletRequestBuilder request, long user) throws Exception {
        return http.perform(request.header("Authorization",
                "Bearer " + tokens.issue(Long.toString(user), "OPERATOR").getTokenValue()));
    }
}
