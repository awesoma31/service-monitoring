package org.awesoma.report.application.port;

public enum AccessDecision {
    ALLOWED,
    DENIED,
    /** Checked separately: a supervisor is allowed everything, even a project that is gone. */
    PROJECT_NOT_FOUND
}
