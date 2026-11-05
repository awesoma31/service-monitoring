package org.awesoma.check.application.port;

import java.time.Instant;
import org.awesoma.check.application.model.ReportPosition;
public interface CursorCodec {
    ReportPosition start(long upperId, Instant capturedAt);
    ReportPosition decode(String value, String scope);
    String next(ReportPosition position, long lastId, String scope);
}
