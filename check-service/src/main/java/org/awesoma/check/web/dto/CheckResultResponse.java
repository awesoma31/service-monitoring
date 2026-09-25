package org.awesoma.check.web.dto;

import java.time.OffsetDateTime;
import org.awesoma.check.domain.CheckResult;
import org.awesoma.check.domain.CheckResultType;

public record CheckResultResponse(
        Long id,
        Long monitorId,
        OffsetDateTime checkedAt,
        CheckResultType result,
        Integer responseMs,
        Integer httpStatus,
        String errorMessage) {

    public static CheckResultResponse of(CheckResult row) {
        return new CheckResultResponse(
                row.id(),
                row.monitorId(),
                row.checkedAt(),
                row.result(),
                row.responseMs(),
                row.httpStatus(),
                row.errorMessage());
    }
}
