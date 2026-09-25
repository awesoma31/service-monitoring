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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper mapper;

    public Page<UserResponse> list(Pageable pageable) {
        return userRepository.findAll(pageable).map(mapper::toResponse);
    }

    public UserResponse get(Long id) {
        return mapper.toResponse(require(id));
    }

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictStateException("Email %s is already taken".formatted(request.email()));
        }
        return mapper.toResponse(userRepository.save(mapper.toEntity(request)));
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
        userRepository.delete(require(id));
    }

    public User require(Long id) {
        return userRepository.findById(id).orElseThrow(() -> NotFoundException.of("User", id));
    }
}
