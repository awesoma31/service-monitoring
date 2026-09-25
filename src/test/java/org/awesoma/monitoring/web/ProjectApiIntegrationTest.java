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
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@Transactional
class ProjectApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;

    @Test
    void creatingAUserAnswersWithLocationOfTheNewResource() throws Exception {
        mockMvc.perform(postJson("/api/v1/users", user("created@example.com")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/users/")))
                .andExpect(jsonPath("$.email").value("created@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void creatingAProjectEnrolsItsOwnerAsMember() throws Exception {
        long ownerId = createUser("owner-enrolled@example.com");

        String body = mockMvc.perform(postJson("/api/v1/projects", project(ownerId, "enrolled")))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long projectId = json.readTree(body).get("id").asLong();

        mockMvc.perform(get("/api/v1/projects/{id}/members", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].userId").value(ownerId))
                .andExpect(jsonPath("$.content[0].role").value("OWNER"));
    }

    @Test
    void duplicateSlugIsAConflict() throws Exception {
        long ownerId = createUser("duplicate-slug@example.com");
        mockMvc.perform(postJson("/api/v1/projects", project(ownerId, "duplicate")))
                .andExpect(status().isCreated());

        mockMvc.perform(postJson("/api/v1/projects", project(ownerId, "duplicate")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflicting state"))
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void removingTheLastOwnerIsAConflict() throws Exception {
        long ownerId = createUser("last-owner@example.com");
        long projectId = createProject(ownerId, "last-owner");

        mockMvc.perform(delete("/api/v1/projects/{id}/members/{userId}", projectId, ownerId))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownProjectIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/projects/{id}", 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    @Test
    void deletingAProjectAnswersWithNoContent() throws Exception {
        long projectId = createProject(createUser("deleted@example.com"), "deleted");

        mockMvc.perform(delete("/api/v1/projects/{id}", projectId)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/projects/{id}", projectId)).andExpect(status().isNotFound());
    }

    @Test
    void pageSizeAboveTheCeilingIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/projects").param("size", "51"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pageSizeAtTheCeilingIsAccepted() throws Exception {
        mockMvc.perform(get("/api/v1/projects").param("size", "50")).andExpect(status().isOk());
    }

    @Test
    void invalidBodyIsRejectedBeforeReachingTheService() throws Exception {
        mockMvc.perform(postJson("/api/v1/users", """
                        {"email":"not-an-email","password":"short","fullName":""}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aProjectCanBeListedFetchedAndRenamed() throws Exception {
        long projectId = createProject(createUser("project-crud@example.com"), "project-crud");

        mockMvc.perform(get("/api/v1/projects").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(get("/api/v1/projects/{id}", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("project-crud"));

        mockMvc.perform(put("/api/v1/projects/{id}", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Renamed Project"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed Project"))
                .andExpect(jsonPath("$.slug").value("project-crud"));
    }

    @Test
    void aMemberCanBeAddedPromotedAndRemoved() throws Exception {
        long ownerId = createUser("membership-owner@example.com");
        long memberId = createUser("membership-member@example.com");
        long projectId = createProject(ownerId, "membership-flow");

        mockMvc.perform(postJson("/api/v1/projects/" + projectId + "/members", """
                        {"userId":%d,"role":"VIEWER"}
                        """.formatted(memberId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(memberId))
                .andExpect(jsonPath("$.role").value("VIEWER"));

        mockMvc.perform(put("/api/v1/projects/{id}/members/{userId}", projectId, memberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"EDITOR"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("EDITOR"));

        mockMvc.perform(delete("/api/v1/projects/{id}/members/{userId}", projectId, memberId))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/projects/{id}/members", projectId))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].userId").value(ownerId));
    }

    private long createUser(String email) throws Exception {
        String body = mockMvc.perform(postJson("/api/v1/users", user(email)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private long createProject(long ownerId, String slug) throws Exception {
        String body = mockMvc.perform(postJson("/api/v1/projects", project(ownerId, slug)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postJson(
            String path, String body) {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String user(String email) {
        return """
                {"email":"%s","password":"password123","fullName":"Test User"}""".formatted(email);
    }

    private String project(long ownerId, String slug) {
        return """
                {"ownerId":%d,"name":"Project","slug":"%s"}""".formatted(ownerId, slug);
    }
}
