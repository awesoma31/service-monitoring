package org.awesoma.monitoring.integration;

import feign.FeignException;
import java.util.List;
import org.awesoma.security.UserProfile;
import org.awesoma.security.UserSecurity;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class UserClientFallbackFactory implements FallbackFactory<UserClient> {
    @Override
    public UserClient create(Throwable cause) {
        return new UserClient() {
            @Override public UserSecurity security(Long id) {
                for (Throwable error = cause; error != null; error = error.getCause()) {
                    if (error instanceof FeignException.NotFound) return new UserSecurity(id, "VIEWER", "INACTIVE");
                }
                throw unavailable();
            }
            @Override public List<UserProfile> profiles(List<Long> ids) { throw unavailable(); }
        };
    }
    private ResponseStatusException unavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "User information cannot currently be verified");
    }
}
