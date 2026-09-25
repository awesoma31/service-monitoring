package org.awesoma.monitoring.web;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.awesoma.monitoring.service.UserService;
import org.awesoma.monitoring.web.controller.UserController;
import org.awesoma.monitoring.web.dto.user.UserCreateRequest;
import org.awesoma.monitoring.web.exception.ConflictStateException;
import org.awesoma.monitoring.web.exception.NotFoundException;
import org.awesoma.monitoring.web.access.RoleAuthorizationInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
@Import(ErrorHandlingWebTest.OwnerRoleHeaderConfig.class)
class ErrorHandlingWebTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class OwnerRoleHeaderConfig {

        @Bean
        MockMvcBuilderCustomizer ownerRoleHeader() {
            return builder -> builder.defaultRequest(get("/")
                    .header(RoleAuthorizationInterceptor.ROLE_HEADER, "OWNER"));
        }
    }

    @Autowired private MockMvc mockMvc;
    @MockitoBean private UserService users;

    @Test
    void bodyValidationListsEveryInvalidField() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"short","fullName":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Request validation failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("One or more request values are invalid"))
                .andExpect(jsonPath("$.instance").value("/api/v1/users"))
                .andExpect(jsonPath("$.violations[*].field")
                        .value(containsInAnyOrder("email", "password", "fullName")));

        verify(users, never()).create(any());
    }

    @Test
    void nonPositivePathIdIsAValidationError() throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}", 0))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Request validation failed"))
                .andExpect(jsonPath("$.violations[0].field").value("id"))
                .andExpect(jsonPath("$.violations[0].message").value("must be greater than 0"));

        verify(users, never()).get(any());
    }

    @Test
    void pathIdOfTheWrongTypeIsAParameterError() throws Exception {
        mockMvc.perform(get("/api/v1/users/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid request parameter"))
                .andExpect(jsonPath("$.detail").value("Parameter 'id' has an invalid value"))
                .andExpect(jsonPath("$.violations[0].field").value("id"));
    }

    @Test
    void invalidEnumDoesNotExposeJacksonInternals() throws Exception {
        mockMvc.perform(put("/api/v1/users/{id}", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"User","status":"DELETED"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Malformed request body"))
                .andExpect(jsonPath("$.detail")
                        .value("Request body is malformed or contains an unsupported value"));

        verify(users, never()).update(any(), any());
    }

    @Test
    void missingResourceUsesTheSameProblemShape() throws Exception {
        when(users.get(99L)).thenThrow(NotFoundException.of("User", 99));

        mockMvc.perform(get("/api/v1/users/{id}", 99))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("User 99 not found"))
                .andExpect(jsonPath("$.instance").value("/api/v1/users/99"));
    }

    @Test
    void domainConflictUsesConflictStatus() throws Exception {
        when(users.create(any(UserCreateRequest.class)))
                .thenThrow(new ConflictStateException("Email is already taken"));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUser()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Conflicting state"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Email is already taken"));
    }

    @Test
    void databaseConflictHidesConstraintDetails() throws Exception {
        when(users.create(any(UserCreateRequest.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint users_email_key"));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUser()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Conflicting data"))
                .andExpect(jsonPath("$.detail").value("The request conflicts with existing data"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("users_email_key"))));
    }

    @Test
    void paginationValidationUsesTheSameProblemShape() throws Exception {
        mockMvc.perform(get("/api/v1/users").param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Request validation failed"))
                .andExpect(jsonPath("$.violations[0].field").value("size"));
    }

    private String validUser() {
        return """
                {"email":"user@example.com","password":"password123","fullName":"User"}
                """;
    }
}
