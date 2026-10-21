package org.awesoma.user.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.RSAKey;
import java.util.Map;
import java.util.UUID;
import org.awesoma.security.JwtTokens;
import org.awesoma.security.PasswordHashing;
import org.awesoma.user.domain.entity.ServiceClient;
import org.awesoma.user.domain.entity.User;
import org.awesoma.user.domain.enums.UserRole;
import org.awesoma.user.domain.enums.UserStatus;
import org.awesoma.user.repository.ServiceClientRepository;
import org.awesoma.user.repository.UserRepository;
import org.awesoma.user.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@WithAnonymousUser
@Transactional
class UserSecurityApiIntegrationTest extends AbstractIntegrationTest {
    private static final String HASH = PasswordHashing.encoder().encode("password123");
    @Autowired private MockMvc http;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private ServiceClientRepository clients;
    @Autowired private JwtTokens tokens;
    @Autowired private PasswordEncoder passwords;
    private User supervisor;
    private User operator;

    @BeforeEach
    void fixtures() {
        supervisor = user(UserRole.SUPERVISOR);
        operator = user(UserRole.OPERATOR);
    }

    @Test
    void loginIssuesAnRsaTokenVerifiableUsingOnlyPublicJwks() throws Exception {
        String login = http.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", operator.getEmail(), "password",
                        "password123"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(900)).andReturn().getResponse().getContentAsString();
        String token = json.readTree(login).get("access_token").asText();
        String keys = http.perform(get("/.well-known/jwks.json")).andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].d").doesNotExist())
                .andExpect(jsonPath("$.keys[0].p").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        var publicKey = RSAKey.parse(json.readTree(keys).get("keys").get(0).toString()).toRSAPublicKey();
        var decoded = NimbusJwtDecoder.withPublicKey(publicKey).build().decode(token);
        assertThat(decoded.getSubject()).isEqualTo(operator.getId().toString());
        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("OPERATOR");
        http.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(operator.getId()))
                .andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.password_hash").doesNotExist());
    }

    @Test
    void registrationAndUserCreationRequireSupervisorAndStoreOnlyHashes() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String request = json.writeValueAsString(Map.of("email", email, "password", "new-password123", "full_name",
                "New user"));
        http.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnauthorized());
        for (String path : new String[] {"/api/v1/auth/register", "/api/v1/users"}) {
            http.perform(post(path).header("Authorization", bearer(operator)).contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                    .andExpect(status().isForbidden());
        }
        http.perform(post("/api/v1/auth/register").header("Authorization", bearer(supervisor))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("VIEWER"))
                .andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.password_hash").doesNotExist());
        var stored = users.findByEmail(email).orElseThrow();
        assertThat(passwords.matches("new-password123", stored.getPassword())).isTrue();
        assertThat(stored.getPassword()).isNotEqualTo("new-password123");
        assertThat(json.writeValueAsString(stored)).doesNotContain("password", stored.getPassword());
    }

    @Test
    void serviceCredentialsIssueOnlyServiceTokensAndInternalProfilesExcludeSecrets() throws Exception {
        var client = new ServiceClient();
        client.setId("check-service");
        client.setSecretHash(HASH);
        clients.saveAndFlush(client);
        String result = http.perform(post("/internal/auth/token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"client_id\":\"check-service\",\"client_secret\":\"password123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expires_in").value(300))
                .andExpect(jsonPath("$.client_secret").doesNotExist()).andReturn().getResponse().getContentAsString();
        String service = "Bearer " + json.readTree(result).get("access_token").asText();
        http.perform(get("/internal/users/{id}/security", operator.getId()).header("Authorization", service))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("OPERATOR"))
                .andExpect(jsonPath("$.password_hash").doesNotExist());
        http.perform(post("/internal/users/batch").header("Authorization", service)
                .contentType(MediaType.APPLICATION_JSON)
                        .content("[" + operator.getId() + "]"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].email").value(operator.getEmail()))
                .andExpect(jsonPath("$[0].password_hash").doesNotExist());
        http.perform(get("/api/v1/users").header("Authorization", service)).andExpect(status().isForbidden());
        http.perform(get("/internal/users/{id}/security", operator.getId()).header("Authorization", bearer(supervisor)))
                .andExpect(status().isForbidden());
        http.perform(post("/internal/auth/token").contentType(MediaType.APPLICATION_JSON)
                .content("{\"client_id\":\"check-service\",\"client_secret\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blockingRoleChangesAndSoftDeletionInvalidateExistingUserAccess() throws Exception {
        String token = bearer(operator);
        http.perform(put("/api/v1/users/{id}/role", operator.getId()).header("Authorization", bearer(operator))
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"SUPERVISOR\"}"))
                .andExpect(status().isForbidden());
        http.perform(put("/api/v1/users/{id}/role", operator.getId()).header("Authorization", bearer(supervisor))
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"VIEWER\"}"))
                .andExpect(status().isOk());
        http.perform(get("/api/v1/auth/me").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("VIEWER"));
        operator.setStatus(UserStatus.BLOCKED);
        users.saveAndFlush(operator);
        http.perform(get("/api/v1/auth/me").header("Authorization", token)).andExpect(status().isUnauthorized());
        http.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", operator.getEmail(), "password",
                        "password123"))))
                .andExpect(status().isUnauthorized());
        http.perform(delete("/api/v1/users/{id}", operator.getId()).header("Authorization", bearer(supervisor)))
                .andExpect(status().isNoContent());
        assertThat(users.findById(operator.getId()).orElseThrow().isDeleted()).isTrue();
        http.perform(get("/api/v1/users/{id}", operator.getId()).header("Authorization", bearer(supervisor)))
                .andExpect(status().isNotFound());
    }

    @Test
    void swaggerExposesUserEndpointsAndBearerAuthentication() throws Exception {
        http.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/auth/register'].post").exists())
                .andExpect(jsonPath("$.paths['/internal/auth/token']").doesNotExist());
    }

    private User user(UserRole role) {
        User user = new User();
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setFullName(role.name());
        user.setPassword(HASH);
        user.setRole(role);
        return users.saveAndFlush(user);
    }
    private String bearer(User user) {
        return "Bearer " + tokens.issue(user.getId().toString(), user.getRole().name()).getTokenValue();
    }
}
