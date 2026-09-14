package org.awesoma.monitoring.web.dto.incident;

import java.time.OffsetDateTime;
import org.awesoma.monitoring.domain.enums.CheckResultType;

public record CheckResultResponse(
        Long id,
        Long monitorId,
        OffsetDateTime checkedAt,
        CheckResultType result,
        Integer responseMs,
        Integer httpStatus,
        String errorMessage) {
}
