package org.awesoma.report.domain;

/** Safe reasons shown to users; never a stack trace or a storage detail. */
public enum FailureCode {
    /** Storing an uploaded file did not complete. */
    UPLOAD_FAILED,
    /** "All monitors" was requested, but the project had none when the worker started. */
    NO_MONITORS,
    /** A requested monitor no longer belongs to the project. */
    MONITOR_NOT_IN_PROJECT,
    /** A configured limit on period, monitors, rows, time or result size was exceeded. */
    LIMIT_EXCEEDED,
    /** The author lost the right to write to the project. */
    ACCESS_REVOKED,
    /** A data source stayed unavailable after the allowed attempts. */
    SOURCE_UNAVAILABLE,
    /** The object storage stayed unavailable after the allowed attempts. */
    STORAGE_UNAVAILABLE,
    /** An attempt did not finish within the time limit after the allowed attempts. */
    TIMED_OUT,
    /** The storage confirmed that the object of a ready report is gone. */
    OBJECT_MISSING,
    /** An unexpected error that persisted through the allowed attempts. */
    GENERATION_ERROR
}
