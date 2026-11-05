package org.awesoma.monitoring.application.model;
public record UserSecurity(Long id, String role, String status) {
    public boolean active() {
        return "ACTIVE".equals(status);
    }
}
