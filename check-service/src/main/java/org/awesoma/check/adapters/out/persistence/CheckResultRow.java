package org.awesoma.check.adapters.out.persistence;

import java.time.OffsetDateTime;
import org.awesoma.check.domain.CheckResult;
import org.awesoma.check.domain.CheckResultType;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("check_results")
public record CheckResultRow(@Id Long id, Long monitorId, OffsetDateTime checkedAt, CheckResultType result,
        Integer responseMs, Integer httpStatus, String errorMessage) {
    public static CheckResultRow from(CheckResult result) {
        return new CheckResultRow(result.id(), result.monitorId(), result.checkedAt(), result.result(),
                result.responseMs(), result.httpStatus(), result.errorMessage());
    }
    public CheckResult toDomain() {
        return new CheckResult(id, monitorId, checkedAt, result, responseMs, httpStatus, errorMessage);
    }
}
