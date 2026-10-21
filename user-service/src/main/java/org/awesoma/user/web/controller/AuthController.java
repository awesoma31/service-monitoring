package org.awesoma.user.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.awesoma.user.service.AuthService;
import org.awesoma.user.service.UserService;
import org.awesoma.user.web.dto.user.LoginRequest;
import org.awesoma.user.web.dto.user.TokenResponse;
import org.awesoma.user.web.dto.user.UserCreateRequest;
import org.awesoma.user.web.dto.user.UserResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {
    private final AuthService auth;
    private final UserService users;

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Log in and receive a JWT Bearer token")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Get the currently authenticated user without password fields")
    public UserResponse me(Authentication authentication) {
        return users.get(Long.parseLong(authentication.getName()));
    }

    @PostMapping("/register")
    @PreAuthorize("hasRole('SUPERVISOR')")
    @Operation(summary = "Register a user", description = "Only supervisors may register new users.")
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody UserCreateRequest request) {
        var created = users.create(request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.id())).body(created);
    }
}
