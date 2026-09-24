package org.awesoma.monitoring.domain.enums;

public enum CheckResultType {
    SUCCESS,
    TIMEOUT,
    BAD_STATUS,
    CONNECTION_ERROR;

    public boolean isFailure() {
        return this != SUCCESS;
    }
}
