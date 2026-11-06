package org.awesoma.user.adapters.in.web.dto.user;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}
