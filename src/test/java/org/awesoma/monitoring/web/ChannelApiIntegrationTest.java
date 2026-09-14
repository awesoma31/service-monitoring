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
class ChannelApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;

    @Test
    void createsAChannelUnderItsProject() throws Exception {
        long projectId = createProject("channel-create");

        mockMvc.perform(postJson("/api/v1/projects/" + projectId + "/channels", """
                        {"type":"EMAIL","target":"ops@example.com"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/channels/")))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.projectId").value(projectId));
    }

    @Test
    void theSameDestinationCannotBeRegisteredTwice() throws Exception {
        long projectId = createProject("channel-duplicate");
        mockMvc.perform(postJson("/api/v1/projects/" + projectId + "/channels", """
                        {"type":"WEBHOOK","target":"https://hooks.example.com/a"}"""))
                .andExpect(status().isCreated());

        mockMvc.perform(postJson("/api/v1/projects/" + projectId + "/channels", """
                        {"type":"WEBHOOK","target":"https://hooks.example.com/a"}"""))
                .andExpect(status().isConflict());
    }

    @Test
    void aChannelCanBeSilencedAndRetargeted() throws Exception {
        long projectId = createProject("channel-update");
        String body = mockMvc.perform(postJson("/api/v1/projects/" + projectId + "/channels", """
                        {"type":"TELEGRAM","target":"@oncall"}"""))
                .andReturn().getResponse().getContentAsString();
        long channelId = json.readTree(body).get("id").asLong();

        mockMvc.perform(put("/api/v1/channels/{id}", channelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"target":"@oncall-backup","enabled":false}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.target").value("@oncall-backup"))
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(delete("/api/v1/channels/{id}", channelId)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/channels/{id}", channelId)).andExpect(status().isNotFound());
    }

    @Test
    void listingIsScopedToTheProject() throws Exception {
        long projectId = createProject("channel-list");
        mockMvc.perform(postJson("/api/v1/projects/" + projectId + "/channels", """
                        {"type":"EMAIL","target":"a@example.com"}"""));

        mockMvc.perform(get("/api/v1/projects/{id}/channels", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/v1/projects/{id}/channels", 999_999))
                .andExpect(status().isNotFound());
    }

    @Test
    void anUnknownChannelTypeIsRejected() throws Exception {
        long projectId = createProject("channel-bad-type");

        mockMvc.perform(postJson("/api/v1/projects/" + projectId + "/channels", """
                        {"type":"CARRIER_PIGEON","target":"coop"}"""))
                .andExpect(status().isBadRequest());
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

    private MockHttpServletRequestBuilder postJson(String path, String body) {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
