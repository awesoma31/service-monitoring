package org.awesoma.user.application.port;
public interface PasswordHasher { String encode(String raw); boolean matches(String raw, String hash); }
