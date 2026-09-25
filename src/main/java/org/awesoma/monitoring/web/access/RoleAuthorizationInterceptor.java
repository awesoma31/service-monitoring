package org.awesoma.monitoring.web.access;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Locale;
import org.awesoma.monitoring.domain.enums.MemberRole;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RoleAuthorizationInterceptor implements HandlerInterceptor {

    public static final String ROLE_HEADER = "X-User-Role";

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        AllowedRoles allowedRoles = findAllowedRoles(handlerMethod);
        if (allowedRoles == null) {
            return true;
        }
        MemberRole role = parseRole(request.getHeader(ROLE_HEADER));
        if (Arrays.stream(allowedRoles.value()).noneMatch(role::equals)) {
            throw new RoleAccessDeniedException(
                    "Role %s cannot call %s %s"
                            .formatted(role, request.getMethod(), request.getRequestURI()));
        }
        return true;
    }

    private AllowedRoles findAllowedRoles(HandlerMethod handlerMethod) {
        AllowedRoles methodRoles = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(), AllowedRoles.class);
        return methodRoles != null
                ? methodRoles
                : AnnotatedElementUtils.findMergedAnnotation(
                        handlerMethod.getBeanType(), AllowedRoles.class);
    }

    private MemberRole parseRole(String value) {
        if (value == null || value.isBlank()) {
            throw new MissingRoleException("Header %s is required".formatted(ROLE_HEADER));
        }
        try {
            return MemberRole.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new MissingRoleException(
                    "Header %s contains an unknown role".formatted(ROLE_HEADER));
        }
    }
}
