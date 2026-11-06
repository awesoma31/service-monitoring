package org.awesoma.report.application.port;

/**
 * The local knowledge that a project was deleted, and the lock that orders deletion against
 * creating and finishing reports of the project. Both are used inside a short transaction.
 */
public interface ProjectGuard {

    /** Held until the current transaction ends; shared by every report of the project. */
    void lock(long projectId);

    boolean deleted(long projectId);

    void markDeleted(long projectId);
}
