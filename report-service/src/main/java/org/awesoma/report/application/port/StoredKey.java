package org.awesoma.report.application.port;

import java.time.Instant;

public record StoredKey(String key, Instant lastModified) {
}
