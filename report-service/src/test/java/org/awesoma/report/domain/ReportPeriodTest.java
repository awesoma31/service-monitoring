package org.awesoma.report.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class ReportPeriodTest {
    private static final Instant FROM = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-02T00:00:00Z");
    private static final ReportPeriod PERIOD = new ReportPeriod(FROM, TO);
    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");
    private static final Duration MAX = Duration.ofDays(31);

    @Test
    void normalisesOffsetsToUtc() {
        var period = ReportPeriod.requested(OffsetDateTime.parse("2026-10-01T00:00:00Z"),
                OffsetDateTime.parse("2026-10-02T03:00:00+03:00"), NOW, MAX);

        assertThat(period).isEqualTo(PERIOD);
        assertThat(period.length()).isEqualTo(Duration.ofDays(1));
    }

    @Test
    void requiresBothBounds() {
        assertThatThrownBy(() -> ReportPeriod.requested(null, OffsetDateTime.parse("2026-10-02T00:00:00Z"), NOW, MAX))
                .isInstanceOf(InvalidReportRequestException.class).extracting("field").isEqualTo("from");
        assertThatThrownBy(() -> ReportPeriod.requested(OffsetDateTime.parse("2026-10-01T00:00:00Z"), null, NOW, MAX))
                .isInstanceOf(InvalidReportRequestException.class).extracting("field").isEqualTo("to");
    }

    @Test
    void rejectsEmptyAndReversedPeriods() {
        assertThatThrownBy(() -> new ReportPeriod(FROM, FROM)).isInstanceOf(InvalidReportRequestException.class);
        assertThatThrownBy(() -> new ReportPeriod(TO, FROM)).isInstanceOf(InvalidReportRequestException.class);
    }

    @Test
    void mayEndAtTheRequestTimeButNotAfterIt() {
        var start = OffsetDateTime.parse("2026-11-01T12:00:00Z");

        assertThat(ReportPeriod.requested(start, OffsetDateTime.parse("2026-11-02T12:00:00Z"), NOW, MAX).to())
                .isEqualTo(NOW);
        assertThatThrownBy(() -> ReportPeriod.requested(start, OffsetDateTime.parse("2026-11-02T12:00:01Z"), NOW, MAX))
                .isInstanceOf(InvalidReportRequestException.class).hasMessageContaining("future");
    }

    @Test
    void limitsTheLength() {
        var start = OffsetDateTime.parse("2026-10-01T00:00:00Z");

        assertThat(ReportPeriod.requested(start, start.plusDays(31), NOW, MAX).length()).isEqualTo(MAX);
        assertThatThrownBy(() -> ReportPeriod.requested(start, start.plusDays(31).plusSeconds(1), NOW, MAX))
                .isInstanceOf(InvalidReportRequestException.class).hasMessageContaining("31 days");
    }

    @Test
    void containsItsStartButNotItsEnd() {
        assertThat(PERIOD.contains(FROM)).isTrue();
        assertThat(PERIOD.contains(TO.minusMillis(1))).isTrue();
        assertThat(PERIOD.contains(TO)).isFalse();
        assertThat(PERIOD.contains(FROM.minusMillis(1))).isFalse();
    }

    @Test
    void includesIncidentsWhoseIntervalOverlapsThePeriod() {
        Instant before = FROM.minusSeconds(3600);
        Instant inside = FROM.plusSeconds(3600);
        Instant after = TO.plusSeconds(3600);

        assertThat(PERIOD.overlaps(before, null)).as("opened earlier, still open").isTrue();
        assertThat(PERIOD.overlaps(before, inside)).as("opened earlier, closed inside").isTrue();
        assertThat(PERIOD.overlaps(inside, after)).as("closed after the period").isTrue();
        assertThat(PERIOD.overlaps(before, after)).as("spans the period").isTrue();
        assertThat(PERIOD.overlaps(FROM, FROM.plusSeconds(1))).as("starts exactly at from").isTrue();
        assertThat(PERIOD.overlaps(before, FROM)).as("closed exactly at from").isFalse();
        assertThat(PERIOD.overlaps(TO, null)).as("starts exactly at to").isFalse();
        assertThat(PERIOD.overlaps(before, before.plusSeconds(1))).as("over before the period").isFalse();
    }

    @Test
    void anIncidentClosedExactlyAtTheEndIsNotOpenAtTheEnd() {
        assertThat(PERIOD.openAtEnd(null)).isTrue();
        assertThat(PERIOD.openAtEnd(TO.plusMillis(1))).isTrue();
        assertThat(PERIOD.openAtEnd(TO)).isFalse();
        assertThat(PERIOD.openAtEnd(TO.minusMillis(1))).isFalse();
    }
}
