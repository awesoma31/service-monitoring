package org.awesoma.monitoring.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

@AutoConfigureMockMvc
@Transactional
class MonitorApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;

    @Test
    void listingReportsTheTotalCountInAHeaderWhilePagingTheBody() throws Exception {
        long projectId = createProject("total-count");
        createMonitor(projectId, "First", "https://first.example");
        createMonitor(projectId, "Second", "https://second.example");
        createMonitor(projectId, "Third", "https://third.example");

        mockMvc.perform(get("/api/v1/projects/{id}/monitors", projectId).param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "3"))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void creatingAMonitorAnswersWithLocationAndItsTags() throws Exception {
        long projectId = createProject("with-tags");

        mockMvc.perform(postJson("/api/v1/projects/" + projectId + "/monitors", """
                        {"name":"Tagged","url":"https://tagged.example","intervalSec":60,
                         "timeoutMs":5000,"tags":["prod","api"]}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/monitors/")))
                .andExpect(jsonPath("$.tags.length()").value(2))
                .andExpect(jsonPath("$.httpMethod").value("GET"))
                .andExpect(jsonPath("$.expectedStatus").value(200))
                .andExpect(jsonPath("$.currentState").value("UNKNOWN"));
    }

    @Test
    void tagsCanBeReplacedWholesale() throws Exception {
        long projectId = createProject("retag");
        long monitorId = createMonitor(projectId, "Retag", "https://retag.example");

        mockMvc.perform(put("/api/v1/monitors/{id}/tags", monitorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tags":["staging"]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags.length()").value(1))
                .andExpect(jsonPath("$.tags[0]").value("staging"));
    }

    @Test
    void listingCanBeNarrowedToASingleTag() throws Exception {
        long projectId = createProject("by-tag");
        createTagged(projectId, "Prod one", "https://p1.example", "prod");
        createTagged(projectId, "Prod two", "https://p2.example", "prod");
        createTagged(projectId, "Staging", "https://s.example", "staging");

        mockMvc.perform(get("/api/v1/projects/{id}/monitors", projectId).param("tag", "prod"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$.content.length()").value(2));

        mockMvc.perform(get("/api/v1/projects/{id}/monitors", projectId).param("tag", "staging"))
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$.content[0].name").value("Staging"));
    }

    @Test
    void anUnknownTagYieldsAnEmptyPageRatherThanAnError() throws Exception {
        long projectId = createProject("unknown-tag");
        createTagged(projectId, "Only", "https://only.example", "prod");

        mockMvc.perform(get("/api/v1/projects/{id}/monitors", projectId).param("tag", "absent"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "0"))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void duplicateNameInsideOneProjectIsAConflict() throws Exception {
        long projectId = createProject("duplicate-name");
        createMonitor(projectId, "Same", "https://same.example");

        mockMvc.perform(postJson("/api/v1/projects/" + projectId + "/monitors", """
                        {"name":"Same","url":"https://other.example","intervalSec":60,"timeoutMs":5000}"""))
                .andExpect(status().isConflict());
    }

    @Test
    void anIntervalBelowTheAllowedMinimumIsRejected() throws Exception {
        long projectId = createProject("bad-interval");

        mockMvc.perform(postJson("/api/v1/projects/" + projectId + "/monitors", """
                        {"name":"Fast","url":"https://fast.example","intervalSec":1,"timeoutMs":5000}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("intervalSec"));
    }

    @Test
    void listingMonitorsOfAMissingProjectIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/projects/{id}/monitors", 999_999))
                .andExpect(status().isNotFound());
    }

    @Test
    void aMonitorCanBeFetchedPausedResumedAndDeleted() throws Exception {
        long projectId = createProject("monitor-crud");
        long monitorId = createMonitor(projectId, "Original", "https://original.example");

        mockMvc.perform(get("/api/v1/monitors/{id}", monitorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Original"));

        updateMonitor(monitorId, "Paused", false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Paused"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.currentState").value("PAUSED"));

        updateMonitor(monitorId, "Resumed", true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.currentState").value("UNKNOWN"));

        mockMvc.perform(delete("/api/v1/monitors/{id}", monitorId))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/monitors/{id}", monitorId))
                .andExpect(status().isNotFound());
    }

    private long createProject(String slug) throws Exception {
        String userBody = mockMvc.perform(postJson("/api/v1/users", """
                        {"email":"%s@example.com","password":"password123","fullName":"Owner"}"""
                        .formatted(slug)))
                .andReturn().getResponse().getContentAsString();
        long ownerId = json.readTree(userBody).get("id").asLong();

        String projectBody = mockMvc.perform(postJson("/api/v1/projects", """
                        {"ownerId":%d,"name":"Project","slug":"%s"}""".formatted(ownerId, slug)))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(projectBody).get("id").asLong();
    }

    private long createMonitor(long projectId, String name, String url) throws Exception {
        String body = mockMvc.perform(postJson("/api/v1/projects/" + projectId + "/monitors", """
                        {"name":"%s","url":"%s","intervalSec":60,"timeoutMs":5000}"""
                        .formatted(name, url)))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private void createTagged(long projectId, String name, String url, String tag) throws Exception {
        mockMvc.perform(postJson("/api/v1/projects/" + projectId + "/monitors", """
                        {"name":"%s","url":"%s","intervalSec":60,"timeoutMs":5000,"tags":["%s"]}"""
                        .formatted(name, url, tag)))
                .andExpect(status().isCreated());
    }

    private MockHttpServletRequestBuilder postJson(String path, String body) {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private org.springframework.test.web.servlet.ResultActions updateMonitor(
            long monitorId, String name, boolean active) throws Exception {
        return mockMvc.perform(put("/api/v1/monitors/{id}", monitorId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s","url":"https://updated.example","httpMethod":"HEAD",
                         "intervalSec":120,"timeoutMs":3000,"expectedStatus":204,"active":%s}
                        """.formatted(name, active)));
    }
}
