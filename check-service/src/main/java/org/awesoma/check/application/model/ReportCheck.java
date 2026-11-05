package org.awesoma.check.application.model;

import java.time.Instant;
public record ReportCheck(long id, long monitorId, String result, Integer responseMs, Instant checkedAt) { }
