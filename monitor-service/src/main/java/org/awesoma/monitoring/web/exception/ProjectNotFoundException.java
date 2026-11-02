package org.awesoma.monitoring.web.exception;

/** A missing project, reported with a code that report-service relies on. */
public class ProjectNotFoundException extends NotFoundException {

    public ProjectNotFoundException(long projectId) {
        super("Project %d not found".formatted(projectId));
    }
}
