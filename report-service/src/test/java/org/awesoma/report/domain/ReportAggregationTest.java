package org.awesoma.report.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.awesoma.report.domain.content.MonitorRow;
import org.awesoma.report.domain.content.ReportAggregation;
import org.awesoma.report.domain.source.CheckOutcome;
import org.awesoma.report.domain.source.CheckRecord;
import org.awesoma.report.domain.source.IncidentRecord;
import org.awesoma.report.domain.source.IncidentSeverity;
import org.awesoma.report.domain.source.MonitorInfo;
import org.awesoma.report.domain.source.ProjectInfo;
import org.junit.jupiter.api.Test;

class ReportAggregationTest {
    private static final Instant FROM = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-02T00:00:00Z");
    private static final ReportPeriod PERIOD = new ReportPeriod(FROM, TO);
    private static final MonitorInfo SITE = new MonitorInfo(11, "Сайт", "https://example.com");
    private static final MonitorInfo IDLE = new MonitorInfo(12, "Idle", "https://idle.example.com");

    private final ReportAggregation aggregation =
            new ReportAggregation(new ProjectInfo(42, "Проект"), PERIOD, List.of(SITE, IDLE));
    private long ids;

    @Test
    void countsOutcomesAndResponseTimesOfEveryMeasuredCheck() {
        check(SITE, FROM, CheckOutcome.SUCCESS, 100L);
        check(SITE, FROM.plusSeconds(1), CheckOutcome.SUCCESS, 0L);
        check(SITE, FROM.plusSeconds(2), CheckOutcome.BAD_STATUS, 251L);
        check(SITE, FROM.plusSeconds(3), CheckOutcome.TIMEOUT, null);
        check(SITE, FROM.plusSeconds(4), CheckOutcome.CONNECTION_ERROR, null);
        check(SITE, FROM.plusSeconds(5), CheckOutcome.SUCCESS, 50L);
        check(SITE, TO, CheckOutcome.SUCCESS, 9999L);
        check(SITE, FROM.minusMillis(1), CheckOutcome.SUCCESS, 9999L);

        var site = row(SITE);
        assertThat(site.checks().total()).isEqualTo(6);
        assertThat(site.checks().success()).isEqualTo(3);
        assertThat(site.checks().badStatus()).isEqualTo(1);
        assertThat(site.checks().timeout()).isEqualTo(1);
        assertThat(site.checks().connectionError()).isEqualTo(1);
        assertThat(site.checks().successRatioPercent()).isEqualByComparingTo("50.00");
        assertThat(site.checks().responseMsMin()).isZero();
        assertThat(site.checks().responseMsMax()).isEqualTo(251);
        assertThat(site.checks().responseMsAvg()).isEqualTo(new BigDecimal("100.25"));
    }

    @Test
    void roundsHalfUpToTwoPlaces() {
        check(SITE, FROM, CheckOutcome.SUCCESS, 1L);
        check(SITE, FROM.plusSeconds(1), CheckOutcome.SUCCESS, 2L);
        check(SITE, FROM.plusSeconds(2), CheckOutcome.TIMEOUT, 2L);

        assertThat(row(SITE).checks().successRatioPercent()).isEqualTo(new BigDecimal("66.67"));
        assertThat(row(SITE).checks().responseMsAvg()).isEqualTo(new BigDecimal("1.67"));
    }

    @Test
    void aMonitorWithoutChecksKeepsItsRowWithEmptyMetrics() {
        var idle = row(IDLE);

        assertThat(idle.checks().total()).isZero();
        assertThat(idle.checks().successRatioPercent()).isNull();
        assertThat(idle.checks().responseMsMin()).isNull();
        assertThat(idle.checks().responseMsAvg()).isNull();
        assertThat(idle.checks().responseMsMax()).isNull();
    }

    @Test
    void countsIncidentsThatOverlapThePeriodAndThoseOpenAtItsEnd() {
        incident(IncidentSeverity.LOW, FROM.minusSeconds(60), null);
        incident(IncidentSeverity.MEDIUM, FROM.minusSeconds(60), FROM.plusSeconds(60));
        incident(IncidentSeverity.HIGH, TO.minusSeconds(60), TO);
        incident(IncidentSeverity.CRITICAL, TO.minusSeconds(60), TO.plusSeconds(1));
        incident(IncidentSeverity.CRITICAL, FROM.minusSeconds(60), FROM);
        incident(IncidentSeverity.HIGH, TO, null);

        var incidents = row(SITE).incidents();
        assertThat(incidents.total()).isEqualTo(4);
        assertThat(List.of(incidents.low(), incidents.medium(), incidents.high(), incidents.critical()))
                .containsExactly(1L, 1L, 1L, 1L);
        assertThat(incidents.openAtEnd()).as("still open, and closed after the end").isEqualTo(2);
    }

    @Test
    void ignoresRowsOfMonitorsOutsideTheReport() {
        aggregation.add(new CheckRecord(1, 99, CheckOutcome.SUCCESS, 1L, FROM));
        aggregation.add(new IncidentRecord(1, 99, IncidentSeverity.LOW, FROM, null));

        var content = aggregation.content(FROM, TO);
        assertThat(content.rows()).extracting(row -> row.monitor().id()).containsExactly(11L, 12L);
        assertThat(content.rows()).allMatch(row -> row.checks().total() == 0 && row.incidents().total() == 0);
        assertThat(aggregation.monitorCount()).isEqualTo(2);
        assertThat(content.project().name()).isEqualTo("Проект");
    }

    private void check(MonitorInfo monitor, Instant at, CheckOutcome outcome, Long responseMs) {
        aggregation.add(new CheckRecord(++ids, monitor.id(), outcome, responseMs, at));
    }

    private void incident(IncidentSeverity severity, Instant startedAt, Instant resolvedAt) {
        aggregation.add(new IncidentRecord(++ids, SITE.id(), severity, startedAt, resolvedAt));
    }

    private MonitorRow row(MonitorInfo monitor) {
        return aggregation.content(FROM, TO).rows().stream()
                .filter(row -> row.monitor().id() == monitor.id()).findFirst().orElseThrow();
    }
}
