package org.awesoma.monitoring.integration;

import java.util.List;
import org.awesoma.security.UserProfile;
import org.awesoma.security.UserSecurity;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "user-service", fallbackFactory = UserClientFallbackFactory.class)
public interface UserClient {
    @GetMapping("/internal/users/{id}/security")
    UserSecurity security(@PathVariable("id") Long id);

    @PostMapping("/internal/users/batch")
    List<UserProfile> profiles(@RequestBody List<Long> ids);
}
