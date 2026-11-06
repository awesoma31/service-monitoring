package org.awesoma.report.domain.content;

public record IncidentStats(long total, long low, long medium, long high, long critical, long openAtEnd) {
}
