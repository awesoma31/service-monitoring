package org.awesoma.monitoring.service;

import lombok.RequiredArgsConstructor;
import org.awesoma.monitoring.domain.entity.User;
import org.awesoma.monitoring.repository.UserRepository;
import org.awesoma.monitoring.web.dto.user.UserCreateRequest;
import org.awesoma.monitoring.web.dto.user.UserResponse;
import org.awesoma.monitoring.web.dto.user.UserUpdateRequest;
import org.awesoma.monitoring.web.exception.ConflictStateException;
import org.awesoma.monitoring.web.exception.NotFoundException;
import org.awesoma.monitoring.web.mapper.UserMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository users;
    private final UserMapper mapper;
    private final PasswordEncoder passwordEncoder;

    public Page<UserResponse> list(Pageable pageable) {
        return users.findAll(pageable).map(mapper::toResponse);
    }

    public UserResponse get(Long id) {
        return mapper.toResponse(require(id));
    }

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        if (users.existsByEmail(request.email())) {
            throw new ConflictStateException("Email %s is already taken".formatted(request.email()));
        }
        User user = mapper.toEntity(request);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        return mapper.toResponse(users.save(user));
    }

    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request) {
        User user = require(id);
        user.setFullName(request.fullName());
        user.setStatus(request.status());
        return mapper.toResponse(user);
    }

    @Transactional
    public void delete(Long id) {
        users.delete(require(id));
    }

    /** Shared lookup so every entry point fails the same way for a missing user. */
    public User require(Long id) {
        return users.findById(id).orElseThrow(() -> NotFoundException.of("User", id));
    }
}
