package org.awesoma.report.application.port;

/** A data source no longer knows the project: it was deleted. */
public class ProjectNotFoundException extends RuntimeException {
    public ProjectNotFoundException(long projectId) {
        super("Project " + projectId + " no longer exists");
    }
}
