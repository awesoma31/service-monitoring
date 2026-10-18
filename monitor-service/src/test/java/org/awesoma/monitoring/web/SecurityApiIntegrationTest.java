package org.awesoma.monitoring.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.awesoma.monitoring.domain.entity.Project;
import org.awesoma.monitoring.domain.entity.ProjectMember;
import org.awesoma.monitoring.repository.ProjectRepository;
import org.awesoma.monitoring.repository.ProjectMemberRepository;
import org.awesoma.monitoring.support.AbstractIntegrationTest;
import org.awesoma.security.JwtTokens;
import org.awesoma.security.UserSecurity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@WithAnonymousUser
@Transactional
class SecurityApiIntegrationTest extends AbstractIntegrationTest {
    @Autowired private MockMvc http;
    @Autowired private ProjectRepository projects;
    @Autowired private ProjectMemberRepository members;
    @Autowired private JwtTokens tokens;
    private Long supervisor;
    private Long operator;
    private Long viewer;
    private Long outsider;
    private Project project;

    @BeforeEach
    void fixtures() {
        supervisor = remoteUser("supervisor@example.com", "SUPERVISOR");
        operator = remoteUser("operator@example.com", "OPERATOR");
        viewer = remoteUser("viewer@example.com", "VIEWER");
        outsider = remoteUser("outsider@example.com", "OPERATOR");
        project = new Project();
        project.setOwnerId(operator);
        project.setName("Private project");
        project.setSlug("secure-" + UUID.randomUUID());
        project.addMember(operator);
        projects.saveAndFlush(project);
        members.saveAndFlush(new ProjectMember(project, viewer));
    }

    @Test
    void anonymousAndInvalidTokensAreUnauthorized() throws Exception {
        http.perform(get("/api/v1/projects")).andExpect(status().isUnauthorized());
        http.perform(get("/api/v1/projects").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
    }

    @Test
    void projectListsAndWritesDependOnMembershipAndOwnership() throws Exception {
        http.perform(get("/api/v1/projects").header("Authorization", bearer(viewer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1));
        http.perform(get("/api/v1/projects").header("Authorization", bearer(outsider)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
        http.perform(get("/api/v1/projects/{id}", project.getId()).header("Authorization", bearer(outsider)))
                .andExpect(status().isForbidden());
        http.perform(get("/api/v1/projects/{id}", project.getId()).header("Authorization", bearer(viewer)))
                .andExpect(status().isOk());
        http.perform(put("/api/v1/projects/{id}/owner-notifications", project.getId()).header("Authorization", bearer(viewer))
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}")).andExpect(status().isForbidden());
        http.perform(put("/api/v1/projects/{id}/owner-notifications", project.getId()).header("Authorization", bearer(operator))
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.owner_notifications_enabled").value(false));
        http.perform(post("/api/v1/projects").header("Authorization", bearer(operator))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"owner_id":%d,"name":"Forbidden","slug":"forbidden-project"}
                        """.formatted(outsider))).andExpect(status().isForbidden());
        members.saveAndFlush(new ProjectMember(project, outsider));
        http.perform(put("/api/v1/projects/{id}/owner-notifications", project.getId()).header("Authorization", bearer(outsider))
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}")).andExpect(status().isForbidden());
    }

    @Test
    void blockingAndRoleChangesAreReadFromUserServiceForExistingTokens() throws Exception {
        String token = bearer(supervisor);
        remoteSecurity.put(supervisor, new UserSecurity(supervisor, "VIEWER", "ACTIVE"));
        http.perform(get("/api/v1/services/check-service/health").header("Authorization", token)).andExpect(status().isForbidden());
        remoteSecurity.put(supervisor, new UserSecurity(supervisor, "VIEWER", "BLOCKED"));
        http.perform(get("/api/v1/projects").header("Authorization", token)).andExpect(status().isUnauthorized());
    }

    @Test
    void userServiceOutagesFailClosedWith503() throws Exception {
        org.mockito.Mockito.when(userClient.security(operator)).thenThrow(
                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));
        http.perform(get("/api/v1/projects").header("Authorization", bearer(operator))).andExpect(status().isServiceUnavailable());
    }

    @Test
    void serviceIdentitiesCanCheckPermissionsButCannotUseTheUserApi() throws Exception {
        String service = "Bearer " + tokens.issue("notification-service", "SERVICE").getTokenValue();
        for (Long user : new Long[] {operator, viewer, outsider, supervisor}) {
            http.perform(get("/internal/access/project/{id}", project.getId()).header("Authorization", service)
                    .param("user_id", user.toString()).param("write", "true"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$").value(user.equals(operator) || user.equals(supervisor)));
        }
        http.perform(get("/internal/projects/{id}/exists", project.getId()).header("Authorization", bearer(supervisor)))
                .andExpect(status().isForbidden());
        http.perform(get("/api/v1/projects").header("Authorization", service)).andExpect(status().isForbidden());
    }

    private String bearer(Long user) {
        return "Bearer " + tokens.issue(user.toString(), remoteSecurity.get(user).role()).getTokenValue();
    }
}
