package org.awesoma.user.web.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nimbusds.jose.jwk.RSAKey;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.awesoma.security.dto.UserSecurity;
import org.awesoma.user.application.usecase.UserService;
import org.awesoma.user.web.mapper.UserMapper;
import org.awesoma.user.application.usecase.ServiceAuthenticationService;
import org.awesoma.user.web.dto.user.TokenResponse;
import org.awesoma.user.web.dto.user.UserResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequiredArgsConstructor
public class InternalUserController {
    private final RSAKey signingKey;
    private final UserService users;
    private final ServiceAuthenticationService authentication;

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> publicKeys() {
        return Map.of("keys", List.of(signingKey.toPublicJWK().toJSONObject()));
    }

    @PostMapping("/internal/auth/token")
    public TokenResponse serviceToken(@Valid @RequestBody ServiceCredentials request) {
        var token = authentication.authenticate(request.clientId(), request.clientSecret());
        return new TokenResponse(token.accessToken(), token.tokenType(), token.expiresIn());
    }

    @GetMapping("/internal/users/{id}/security")
    public UserSecurity security(@PathVariable @Positive Long id) {
        var user = users.raw(id);
        return new UserSecurity(user.id(), user.role().name(),
                user.deleted() ? "INACTIVE" : user.status().name());
    }

    @PostMapping("/internal/users/batch")
    public List<UserResponse> batch(@RequestBody @Size(max = 50) List<@Positive Long> ids) {
        return users.batch(ids).stream().map(UserMapper::toResponse).toList();
    }

    public record ServiceCredentials(@NotBlank @Size(max = 100) String clientId,
            @NotBlank @Size(max = 255) @JsonProperty(
                    access = JsonProperty.Access.WRITE_ONLY) String clientSecret) {}
}
