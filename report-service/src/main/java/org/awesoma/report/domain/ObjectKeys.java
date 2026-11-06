package org.awesoma.report.domain;

import java.util.UUID;

/** Object keys are made of server IDs only, never of a name a user supplied. */
public final class ObjectKeys {

    private ObjectKeys() {
    }

    public static String projectPrefix(long projectId) {
        return "projects/" + projectId + "/reports/";
    }

    public static String upload(long projectId, UUID reportId) {
        return projectPrefix(projectId) + reportId;
    }

    /** A new attempt writes to a new key, so a late writer of an old attempt cannot overwrite it. */
    public static String attempt(long projectId, UUID reportId, UUID attemptId) {
        return projectPrefix(projectId) + reportId + "/attempts/" + attemptId;
    }
}
