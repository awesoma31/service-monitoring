package org.awesoma.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Slf4j
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
            log.debug("User creation rejected: reason=email_already_taken");
            throw new ConflictStateException("Email %s is already taken".formatted(request.email()));
        }
        User user = mapper.toEntity(request);
        user.setPassword(passwords.encode(request.password()));
        User saved = userRepository.save(user);
        logChangeAfterCommit("CREATED", saved);
        return mapper.toResponse(saved);
    }

    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request) {
        User user = require(id);
        user.setFullName(request.fullName());
        user.setStatus(request.status());
        logChangeAfterCommit("UPDATED", user);
        return mapper.toResponse(user);
    }

    @Transactional
    public void delete(Long id) {
        User user = require(id);
        user.setDeleted(true);
        user.setStatus(UserStatus.INACTIVE);
        logChangeAfterCommit("DELETED", user);
    }

    @Transactional
    public UserResponse updateRole(Long id, UserRoleUpdateRequest request) {
        User user = require(id);
        user.setRole(request.role());
        logChangeAfterCommit("ROLE_UPDATED", user);
        return mapper.toResponse(user);
    }

    public User require(Long id) {
        return userRepository.findById(id).filter(user -> !user.isDeleted())
                .orElseThrow(() -> {
                    log.debug("User lookup failed: user_id={}", id);
                    return NotFoundException.of("User", id);
                });
    }

    private void logChangeAfterCommit(String operation, User user) {
        Long userId = user.getId();
        var role = user.getRole();
        var status = user.getStatus();
        Runnable logChange = () -> log.info("User changed: operation={} user_id={} role={} status={}",
                operation, userId, role, status);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    logChange.run();
                }
            });
        } else {
            logChange.run();
        }
    }
}
