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
                .andExpect(jsonPath("$.content[0].user_id").value(ownerId));
    }

    @Test
    void creatingAProjectCanDisableOwnerIncidentNotifications() throws Exception {
        long ownerId = createUser("silent-from-start@example.com");

        mockMvc.perform(postJson("/api/v1/projects", """
                        {
                          "owner_id": %d,
                          "name": "Silent Project",
                          "slug": "silent-from-start",
                          "owner_notifications_enabled": false
                        }
                        """.formatted(ownerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.owner_notifications_enabled").value(false));
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
                        {"email":"not-an-email","password":"short","full_name":""}"""))
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
    void ownerIncidentNotificationsCanBeDisabledAndEnabledAgain() throws Exception {
        long projectId = createProject(createUser("silent-owner@example.com"), "silent-owner");

        mockMvc.perform(get("/api/v1/projects/{id}", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner_notifications_enabled").value(true));

        mockMvc.perform(put("/api/v1/projects/{id}/owner-notifications", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner_notifications_enabled").value(false));

        mockMvc.perform(get("/api/v1/projects/{id}", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner_notifications_enabled").value(false));

        mockMvc.perform(put("/api/v1/projects/{id}/owner-notifications", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner_notifications_enabled").value(true));
    }

    @Test
    void ownerNotificationPreferenceRequiresEnabledFlag() throws Exception {
        long projectId = createProject(createUser("invalid-preference@example.com"), "invalid-preference");

        mockMvc.perform(put("/api/v1/projects/{id}/owner-notifications", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aMemberCanBeAddedAndRemoved() throws Exception {
        long ownerId = createUser("membership-owner@example.com");
        long memberId = createUser("membership-member@example.com");
        long projectId = createProject(ownerId, "membership-flow");

        mockMvc.perform(postJson("/api/v1/projects/" + projectId + "/members", """
                        {"user_id":%d}
                        """.formatted(memberId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user_id").value(memberId))
                .andExpect(jsonPath("$.joined_at").isNotEmpty());

        mockMvc.perform(delete("/api/v1/projects/{id}/members/{userId}", projectId, memberId))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/projects/{id}/members", projectId))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].user_id").value(ownerId));
    }

    @Test
    void projectOwnerCannotBeRemovedFromMembers() throws Exception {
        long ownerId = createUser("irremovable-owner@example.com");
        long projectId = createProject(ownerId, "irremovable-owner");

        mockMvc.perform(delete("/api/v1/projects/{id}/members/{userId}", projectId, ownerId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflicting state"));

        mockMvc.perform(get("/api/v1/projects/{id}/members", projectId))
                .andExpect(jsonPath("$.content[0].user_id").value(ownerId));
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
                {"email":"%s","password":"password123","full_name":"Test User"}""".formatted(email);
    }

    private String project(long ownerId, String slug) {
        return """
                {"owner_id":%d,"name":"Project","slug":"%s"}""".formatted(ownerId, slug);
    }
}
