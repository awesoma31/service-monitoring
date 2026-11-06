package org.awesoma.user.adapters.in.web.mapper;
import org.awesoma.user.domain.User;
import org.awesoma.user.adapters.in.web.dto.user.UserResponse;
public final class UserMapper {
    private UserMapper() {}
    public static UserResponse toResponse(User user) {
        return new UserResponse(user.id(), user.email(), user.fullName(), user.status(), user.role());
    }
}
