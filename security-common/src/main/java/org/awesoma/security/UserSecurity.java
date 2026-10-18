package org.awesoma.security;

public record UserSecurity(Long id, String role, String status) {
    public boolean active() { return "ACTIVE".equals(status); }
}
