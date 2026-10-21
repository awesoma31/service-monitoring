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
import org.awesoma.security.UserSecurity;
import org.awesoma.user.repository.UserRepository;
import org.awesoma.user.service.ServiceAuthenticationService;
import org.awesoma.user.service.UserService;
import org.awesoma.user.web.dto.user.TokenResponse;
import org.awesoma.user.web.dto.user.UserResponse;
import org.awesoma.user.web.exception.NotFoundException;
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
    private final UserRepository repository;
    private final ServiceAuthenticationService authentication;

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> publicKeys() {
        return Map.of("keys", List.of(signingKey.toPublicJWK().toJSONObject()));
    }

    @PostMapping("/internal/auth/token")
    public TokenResponse serviceToken(@Valid @RequestBody ServiceCredentials request) {
        return authentication.authenticate(request.clientId(), request.clientSecret());
    }

    @GetMapping("/internal/users/{id}/security")
    public UserSecurity security(@PathVariable @Positive Long id) {
        // Deleted users must also be reported inactive, so stale JWTs can be rejected.
        var user = repository.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
        return new UserSecurity(user.getId(), user.getRole().name(),
                user.isDeleted() ? "INACTIVE" : user.getStatus().name());
    }

    @PostMapping("/internal/users/batch")
    public List<UserResponse> batch(@RequestBody @Size(max = 50) List<@Positive Long> ids) {
        // This read-only projection retains names for existing memberships after soft deletion.
        return repository.findAllById(ids).stream().map(user -> new UserResponse(user.getId(), user.getEmail(),
                user.getFullName(), user.getStatus(), user.getRole())).toList();
    }

    public record ServiceCredentials(@NotBlank @Size(max = 100) String clientId,
            @NotBlank @Size(max = 255) @JsonProperty(
                    access = JsonProperty.Access.WRITE_ONLY) String clientSecret) {}
}
