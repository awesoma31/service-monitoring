package org.awesoma.check.domain;

import java.time.OffsetDateTime;

public record CheckResult(
        Long id,
        Long monitorId,
        OffsetDateTime checkedAt,
        CheckResultType result,
        Integer responseMs,
        Integer httpStatus,
        String errorMessage) {

    public static CheckResult of(Long monitorId, ProbeOutcome outcome, OffsetDateTime checkedAt) {
        return new CheckResult(
                null,
                monitorId,
                checkedAt,
                outcome.result(),
                outcome.responseMs(),
                outcome.httpStatus(),
                outcome.errorMessage());
    }
}
