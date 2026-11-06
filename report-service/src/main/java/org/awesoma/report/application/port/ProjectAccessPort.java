package org.awesoma.report.application.port;

/** Whether an active user may use a project that still exists, asked of its owner service. */
public interface ProjectAccessPort {

    /** @throws AccessUnavailableException when the answer cannot be obtained; access is then refused */
    AccessDecision check(long projectId, long userId, AccessLevel level);
}
