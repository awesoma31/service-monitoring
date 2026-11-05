package org.awesoma.user.application.model;
public record Token(String accessToken, String tokenType, long expiresIn) {}
