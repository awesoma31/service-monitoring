package org.awesoma.report.domain.content;

import org.awesoma.report.domain.source.MonitorInfo;

/** A selected monitor stays in the report even when it had no checks in the period. */
public record MonitorRow(MonitorInfo monitor, CheckStats checks, IncidentStats incidents) {
}
