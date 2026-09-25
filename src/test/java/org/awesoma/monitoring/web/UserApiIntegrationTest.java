package org.awesoma.monitoring.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class UserApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;

    @Test
    void aUserCanBeFetchedAndListedWithoutPasswordData() throws Exception {
        long userId = createUser("listed-user@example.com", "Listed User");

        mockMvc.perform(get("/api/v1/users/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("listed-user@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(get("/api/v1/users").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].password").doesNotExist())
                .andExpect(jsonPath("$.content[0].password").doesNotExist());
    }

    @Test
    void aUserCanBeUpdated() throws Exception {
        long userId = createUser("updated-user@example.com", "Old Name");

        mockMvc.perform(put("/api/v1/users/{id}", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"New Name","status":"BLOCKED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("New Name"))
                .andExpect(jsonPath("$.status").value("BLOCKED"));

        mockMvc.perform(get("/api/v1/users/{id}", userId))
                .andExpect(jsonPath("$.fullName").value("New Name"))
                .andExpect(jsonPath("$.status").value("BLOCKED"));
    }

    @Test
    void aUserCanBeDeleted() throws Exception {
        long userId = createUser("deleted-user@example.com", "Deleted User");

        mockMvc.perform(delete("/api/v1/users/{id}", userId)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/users/{id}", userId)).andExpect(status().isNotFound());
    }

    @Test
    void duplicateEmailIsAConflict() throws Exception {
        createUser("duplicate-user@example.com", "First User");

        mockMvc.perform(postJson("/api/v1/users", user("duplicate-user@example.com", "Second User")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflicting state"));
    }

    private long createUser(String email, String fullName) throws Exception {
        String body = mockMvc.perform(postJson("/api/v1/users", user(email, fullName)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private MockHttpServletRequestBuilder postJson(String path, String body) {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String user(String email, String fullName) {
        return """
                {"email":"%s","password":"password123","fullName":"%s"}
                """.formatted(email, fullName);
    }
}
