package org.awesoma.monitoring.adapters.out.http;

import feign.FeignException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.awesoma.security.dto.UserProfile;
import org.awesoma.security.dto.UserSecurity;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@Slf4j
public class UserClientFallbackFactory implements FallbackFactory<UserClient> {
    @Override
    public UserClient create(Throwable cause) {
        return new UserClient() {
            @Override public UserSecurity security(Long id) {
                for (Throwable error = cause; error != null; error = error.getCause()) {
                    if (error instanceof FeignException.NotFound) return new UserSecurity(id, "VIEWER", "INACTIVE");
                }
                log.warn("user-service unavailable, user security lookup failed: user_id={}", id, cause);
                throw unavailable();
            }
            @Override public List<UserProfile> profiles(List<Long> ids) {
                log.warn("user-service unavailable, user profiles lookup failed: user_ids={}", ids, cause);
                throw unavailable();
            }
        };
    }
    private ResponseStatusException unavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "User information cannot currently be verified");
    }
}
