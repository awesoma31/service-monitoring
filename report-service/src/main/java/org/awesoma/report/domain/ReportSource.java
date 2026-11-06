package org.awesoma.report.domain;

public enum ReportSource {
    /** A finished file a user attached to the project. */
    UPLOADED,
    /** A file the service builds in the background from monitoring data. */
    GENERATED
}
