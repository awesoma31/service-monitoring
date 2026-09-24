package org.awesoma.monitoring.web;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import org.awesoma.monitoring.config.RoleAccessConfig;
import org.awesoma.monitoring.domain.enums.HttpMethod;
import org.awesoma.monitoring.domain.enums.MonitorState;
import org.awesoma.monitoring.domain.enums.UserStatus;
import org.awesoma.monitoring.service.MonitorService;
import org.awesoma.monitoring.service.UserService;
import org.awesoma.monitoring.web.access.RoleAuthorizationInterceptor;
import org.awesoma.monitoring.web.controller.MonitorController;
import org.awesoma.monitoring.web.controller.UserController;
import org.awesoma.monitoring.web.dto.monitor.MonitorResponse;
import org.awesoma.monitoring.web.dto.user.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({MonitorController.class, UserController.class})
@Import({RoleAccessConfig.class, RoleAuthorizationInterceptor.class})
class RoleAuthorizationWebTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private MonitorService monitorService;
    @MockitoBean private UserService userService;

    @Test
    void missingRoleHeaderIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/monitors/{id}", 1))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Authentication required"));

        verifyNoInteractions(monitorService);
    }

    @Test
    void unknownRoleHeaderIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/monitors/{id}", 1)
                        .header(RoleAuthorizationInterceptor.ROLE_HEADER, "ADMIN"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Header X-User-Role contains an unknown role"));
    }

    @Test
    void viewerCanReadAMonitor() throws Exception {
        when(monitorService.get(1L)).thenReturn(monitor());

        mockMvc.perform(get("/api/v1/monitors/{id}", 1)
                        .header(RoleAuthorizationInterceptor.ROLE_HEADER, "viewer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void viewerCannotDeleteAMonitor() throws Exception {
        mockMvc.perform(delete("/api/v1/monitors/{id}", 1)
                        .header(RoleAuthorizationInterceptor.ROLE_HEADER, "VIEWER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access denied"));

        verifyNoInteractions(monitorService);
    }

    @Test
    void editorCanDeleteAMonitor() throws Exception {
        mockMvc.perform(delete("/api/v1/monitors/{id}", 1)
                        .header(RoleAuthorizationInterceptor.ROLE_HEADER, "EDITOR"))
                .andExpect(status().isNoContent());

        verify(monitorService).delete(1L);
    }

    @Test
    void onlyOwnerCanCreateUsers() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .header(RoleAuthorizationInterceptor.ROLE_HEADER, "EDITOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUser()))
                .andExpect(status().isForbidden());

        when(userService.create(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new UserResponse(1L, "user@example.com", "User", UserStatus.ACTIVE));
        mockMvc.perform(post("/api/v1/users")
                        .header(RoleAuthorizationInterceptor.ROLE_HEADER, "OWNER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validUser()))
                .andExpect(status().isCreated());
    }

    private MonitorResponse monitor() {
        return new MonitorResponse(
                1L,
                1L,
                "Monitor",
                "https://example.com",
                HttpMethod.GET,
                60,
                5000,
                200,
                true,
                MonitorState.UP,
                Set.of());
    }

    private String validUser() {
        return """
                {"email":"user@example.com","password":"password123","fullName":"User"}
                """;
    }
}
