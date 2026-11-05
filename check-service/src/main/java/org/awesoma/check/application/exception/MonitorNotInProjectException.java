package org.awesoma.check.application.exception;

/** A request names a monitor that does not belong to the project. */
public class MonitorNotInProjectException extends RuntimeException {

    public MonitorNotInProjectException(long projectId) {
        super("Not every requested monitor belongs to project " + projectId);
    }
}
