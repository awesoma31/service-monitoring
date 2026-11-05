package org.awesoma.check.adapters.out.cursor;

import java.time.Instant;
import org.awesoma.check.application.model.ReportPosition;
import org.awesoma.check.application.port.CursorCodec;
import org.awesoma.integration.reportdata.ReportDataCursor;
import org.springframework.stereotype.Component;

@Component
public class ReportCursorCodec implements CursorCodec {
    public ReportPosition start(long upperId, Instant at) { return convert(ReportDataCursor.start(upperId, at)); }
    public ReportPosition decode(String value, String scope) { return convert(ReportDataCursor.decode(value, scope)); }
    public String next(ReportPosition position, long id, String scope) {
        return new ReportDataCursor(id, position.upperId(), position.capturedAt()).encode(scope);
    }
    private static ReportPosition convert(ReportDataCursor cursor) {
        return new ReportPosition(cursor.lastId(), cursor.upperId(), cursor.capturedAt());
    }
}
