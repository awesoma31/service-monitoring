package org.awesoma.check.application.model;

import java.time.Instant;
public record ReportPosition(long lastId, long upperId, Instant capturedAt) { }
