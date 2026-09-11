package org.awesoma.monitoring.domain.enums;

/** Outcome of a single probe. Everything other than SUCCESS counts as a failure. */
public enum CheckResultType {
    SUCCESS,
    TIMEOUT,
    BAD_STATUS,
    CONNECTION_ERROR;

    public boolean isFailure() {
        return this != SUCCESS;
    }
}
