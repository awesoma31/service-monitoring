package org.awesoma.monitoring.domain.enums;

/** Lifecycle of an account. Only ACTIVE users can sign in and own projects. */
public enum UserStatus {
    ACTIVE,
    INACTIVE,
    BLOCKED
}
