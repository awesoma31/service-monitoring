package org.awesoma.monitoring.adapters.out.http;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.awesoma.monitoring.application.exception.NotFoundException;
import org.awesoma.monitoring.application.model.UserProfile;
import org.awesoma.monitoring.application.model.UserSecurity;
import org.awesoma.monitoring.application.port.UserDirectoryPort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserDirectory implements UserDirectoryPort {
    private final UserClient client;

    @Override
    public UserSecurity security(Long id) { var user = client.security(id); return new UserSecurity(user.id(), user.role(), user.status()); }

    @Override
    public void requireActive(Long id) {
        if (!security(id).active()) throw NotFoundException.of("Active user", id);
    }

    @Override
    public UserProfile profile(Long id) { return profiles(List.of(id)).get(id); }

    @Override
    public Map<Long, UserProfile> profiles(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        var found = client.profiles(ids).stream().map(profile -> new UserProfile(profile.id(), profile.email(), profile.fullName()))
                .collect(Collectors.toMap(UserProfile::id, Function.identity()));
        if (!found.keySet().containsAll(ids)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Member profiles are unavailable");
        }
        return found;
    }
}
