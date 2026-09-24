package org.awesoma.monitoring.web.mapper;

import org.awesoma.monitoring.domain.entity.User;
import org.awesoma.monitoring.web.dto.user.UserCreateRequest;
import org.awesoma.monitoring.web.dto.user.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserMapper {

    UserResponse toResponse(User user);

    /** The password is hashed by the service, so it never reaches the entity as given. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "status", ignore = true)
    User toEntity(UserCreateRequest request);
}
