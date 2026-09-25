package org.awesoma.monitoring.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class TagApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;

    @Test
    void createsATagAndReportsItsLocation() throws Exception {
        mockMvc.perform(createTag("production"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/tags/")))
                .andExpect(jsonPath("$.name").value("production"));
    }

    @Test
    void refusesADuplicateName() throws Exception {
        mockMvc.perform(createTag("duplicate")).andExpect(status().isCreated());

        mockMvc.perform(createTag("duplicate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflicting state"));
    }

    @Test
    void listingIsPagedAndRefusesAnOversizedPage() throws Exception {
        mockMvc.perform(createTag("listed"));

        mockMvc.perform(get("/api/v1/tags").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        mockMvc.perform(get("/api/v1/tags").param("size", "51")).andExpect(status().isBadRequest());
    }

    @Test
    void deletesAnExistingTag() throws Exception {
        String body = mockMvc.perform(createTag("removable"))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).get("id").asLong();

        mockMvc.perform(delete("/api/v1/tags/{id}", id)).andExpect(status().isNoContent());
    }

    @Test
    void deletingAMissingTagIsNotFound() throws Exception {
        mockMvc.perform(delete("/api/v1/tags/{id}", 999_999)).andExpect(status().isNotFound());
    }

    @Test
    void aBlankNameIsRejected() throws Exception {
        mockMvc.perform(createTag(" ")).andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder createTag(
            String name) {
        return post("/api/v1/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s"}""".formatted(name));
    }
}
