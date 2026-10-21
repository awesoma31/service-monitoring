package org.awesoma.user.service;

import lombok.RequiredArgsConstructor;
import org.awesoma.user.domain.entity.User;
import org.awesoma.user.domain.enums.UserStatus;
import org.awesoma.user.repository.UserRepository;
import org.awesoma.user.web.dto.user.UserCreateRequest;
import org.awesoma.user.web.dto.user.UserResponse;
import org.awesoma.user.web.dto.user.UserRoleUpdateRequest;
import org.awesoma.user.web.dto.user.UserUpdateRequest;
import org.awesoma.user.web.exception.ConflictStateException;
import org.awesoma.user.web.exception.NotFoundException;
import org.awesoma.user.web.mapper.UserMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper mapper;
    private final PasswordEncoder passwords;

    public Page<UserResponse> list(Pageable pageable) {
        return userRepository.findByDeletedFalse(pageable).map(mapper::toResponse);
    }

    public UserResponse get(Long id) {
        return mapper.toResponse(require(id));
    }

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictStateException("Email %s is already taken".formatted(request.email()));
        }
        User user = mapper.toEntity(request);
        user.setPassword(passwords.encode(request.password()));
        return mapper.toResponse(userRepository.save(user));
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
        User user = require(id);
        user.setDeleted(true);
        user.setStatus(UserStatus.INACTIVE);
    }

    @Transactional
    public UserResponse updateRole(Long id, UserRoleUpdateRequest request) {
        User user = require(id);
        user.setRole(request.role());
        return mapper.toResponse(user);
    }

    public User require(Long id) {
        return userRepository.findById(id).filter(user -> !user.isDeleted())
                .orElseThrow(() -> NotFoundException.of("User", id));
    }
}
