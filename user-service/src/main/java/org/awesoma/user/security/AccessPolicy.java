package org.awesoma.user.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component("access")
public class AccessPolicy {
    public boolean self(Long id) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getAuthorities().stream().anyMatch(value -> value.getAuthority().equals("ROLE_SUPERVISOR"))
                || id.toString().equals(authentication.getName());
    }
}
