package org.awesoma.monitoring.web;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.awesoma.monitoring.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** The contract check-service relies on: due targets in, probe outcomes back. */
@AutoConfigureMockMvc
@Transactional
class InternalMonitorApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;

    @Test
    void aNewMonitorIsListedAsDueWithWhatTheProbeNeeds() throws Exception {
        long monitorId = createMonitor("internal-due");

        mockMvc.perform(get("/internal/monitors/due")
                        .param("limit", "50")
                        .param("lease_ms", "90000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].monitor_id").value(hasItem((int) monitorId)))
                .andExpect(jsonPath("$[?(@.monitor_id == %d)].url".formatted(monitorId))
                        .value(hasItem("https://internal.example")))
                .andExpect(jsonPath("$[?(@.monitor_id == %d)].timeout_ms".formatted(monitorId))
                        .value(hasItem(5000)))
                .andExpect(jsonPath("$[?(@.monitor_id == %d)].claim_token".formatted(monitorId))
                        .isNotEmpty());
    }

    @Test
    void aReportedFailureOpensAnIncident() throws Exception {
        long monitorId = createMonitor("internal-outcome");
        String claimToken = claimToken(monitorId);

        mockMvc.perform(postJson("/internal/monitors/" + monitorId + "/outcomes", """
                        {"result":"CONNECTION_ERROR","response_ms":7,"error_message":"refused",
                         "claim_token":"%s"}""".formatted(claimToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/monitors/{id}", monitorId))
                .andExpect(jsonPath("$.current_state").value("DOWN"));
        mockMvc.perform(get("/api/v1/monitors/{id}/incidents", monitorId))
                .andExpect(jsonPath("$.content[0].status").value("OPEN"))
                .andExpect(jsonPath("$.content[0].cause").value("refused"));
    }

    @Test
    void anInvalidOutcomeIsRejectedWithAReadableValidationError() throws Exception {
        long monitorId = createMonitor("invalid-internal-outcome");

        mockMvc.perform(postJson("/internal/monitors/" + monitorId + "/outcomes", "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Request validation failed"))
                .andExpect(jsonPath("$.violations[*].field").value(hasItem("result")));
    }

    @Test
    void existenceIsReportedWithoutAnError() throws Exception {
        long monitorId = createMonitor("internal-exists");

        mockMvc.perform(get("/internal/monitors/{id}/exists", monitorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(true));
        mockMvc.perform(get("/internal/monitors/{id}/exists", 999_999))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(false));
    }

    @Test
    void projectsAndIncidentsCanBeLookedUpById() throws Exception {
        long monitorId = createMonitor("internal-lookup");
        String claimToken = claimToken(monitorId);
        mockMvc.perform(postJson("/internal/monitors/" + monitorId + "/outcomes", """
                        {"result":"TIMEOUT","response_ms":1000,"error_message":"slow",
                         "claim_token":"%s"}""".formatted(claimToken)))
                .andExpect(status().isNoContent());
        String incidents = mockMvc.perform(get("/api/v1/monitors/{id}/incidents", monitorId))
                .andReturn().getResponse().getContentAsString();
        long incidentId = json.readTree(incidents).get("content").get(0).get("id").asLong();
        String monitor = mockMvc.perform(get("/api/v1/monitors/{id}", monitorId))
                .andReturn().getResponse().getContentAsString();
        long projectId = json.readTree(monitor).get("project_id").asLong();

        mockMvc.perform(get("/internal/projects/{id}/exists", projectId)).andExpect(jsonPath("$").value(true));
        mockMvc.perform(get("/internal/incidents/{id}/exists", incidentId)).andExpect(jsonPath("$").value(true));
        mockMvc.perform(get("/internal/projects/{id}/exists", 999_999)).andExpect(jsonPath("$").value(false));
        mockMvc.perform(get("/internal/incidents/{id}/exists", 999_999)).andExpect(jsonPath("$").value(false));
    }

    private long createMonitor(String slug) throws Exception {
        String user = mockMvc.perform(postJson("/api/v1/users", """
                        {"email":"%s@example.com","password":"password123","full_name":"Owner"}"""
                        .formatted(slug)))
                .andReturn().getResponse().getContentAsString();
        String project = mockMvc.perform(postJson("/api/v1/projects", """
                        {"owner_id":%d,"name":"Project","slug":"%s"}"""
                        .formatted(json.readTree(user).get("id").asLong(), slug)))
                .andReturn().getResponse().getContentAsString();
        String monitor = mockMvc.perform(postJson(
                        "/api/v1/projects/" + json.readTree(project).get("id").asLong() + "/monitors",
                        """
                        {"name":"Internal","url":"https://internal.example"}"""))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(monitor).get("id").asLong();
    }

    private String claimToken(long monitorId) throws Exception {
        String targets = mockMvc.perform(get("/internal/monitors/due")
                        .param("limit", "50")
                        .param("lease_ms", "90000"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        for (var target : json.readTree(targets)) {
            if (target.get("monitor_id").asLong() == monitorId) {
                return target.get("claim_token").asText();
            }
        }
        throw new AssertionError("Monitor %d was not claimed".formatted(monitorId));
    }

    private MockHttpServletRequestBuilder postJson(String path, String body) {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
