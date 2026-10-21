package org.awesoma.monitoring.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.awesoma.monitoring.integration.UserClient;
import org.awesoma.monitoring.web.exception.NotFoundException;
import org.awesoma.security.UserProfile;
import org.awesoma.security.UserSecurity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserDirectory {
    private final UserClient client;

    public UserSecurity security(Long id) { return client.security(id); }

    public void requireActive(Long id) {
        if (!security(id).active()) throw NotFoundException.of("Active user", id);
    }

    public UserProfile profile(Long id) { return profiles(List.of(id)).get(id); }

    public Map<Long, UserProfile> profiles(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        var found = client.profiles(ids).stream().collect(Collectors.toMap(UserProfile::id, Function.identity()));
        if (!found.keySet().containsAll(ids)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Member profiles are unavailable");
        }
        return found;
    }
}
