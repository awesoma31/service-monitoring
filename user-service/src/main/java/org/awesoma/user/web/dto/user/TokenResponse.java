package org.awesoma.user.web.dto.user;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}
