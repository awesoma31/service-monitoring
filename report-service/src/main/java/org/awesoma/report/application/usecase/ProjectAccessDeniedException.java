package org.awesoma.report.application.usecase;

public class ProjectAccessDeniedException extends RuntimeException {
    public ProjectAccessDeniedException() {
        super("No access to this project");
    }
}
