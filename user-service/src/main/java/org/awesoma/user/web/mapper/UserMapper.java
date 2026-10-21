package org.awesoma.user.web.mapper;

import org.awesoma.user.domain.entity.User;
import org.awesoma.user.web.dto.user.UserCreateRequest;
import org.awesoma.user.web.dto.user.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserMapper {

    UserResponse toResponse(User user);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    User toEntity(UserCreateRequest request);
}
