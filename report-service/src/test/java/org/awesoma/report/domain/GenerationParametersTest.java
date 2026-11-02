package org.awesoma.report.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class GenerationParametersTest {
    private static final ReportPeriod PERIOD = new ReportPeriod(
            Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-02T00:00:00Z"));
    private static final Instant REQUESTED = Instant.parse("2026-11-02T12:00:00Z");

    @Test
    void withoutIdsSelectsEveryMonitor() {
        var selection = MonitorSelection.requested(null);

        assertThat(selection.all()).isTrue();
        assertThat(selection.size()).isZero();
        assertThat(selection.canonical()).isEqualTo("all");
    }

    @Test
    void ordersAndDeduplicatesExplicitIds() {
        var selection = MonitorSelection.requested(List.of(12L, 11L, 12L));

        assertThat(selection.monitorIds()).containsExactly(11L, 12L);
        assertThat(selection.canonical()).isEqualTo("ids:11,12");
    }

    @Test
    void rejectsAnExplicitEmptyListAndInvalidIds() {
        assertThatThrownBy(() -> MonitorSelection.requested(List.of()))
                .isInstanceOf(InvalidReportRequestException.class).extracting("field").isEqualTo("monitor_ids");
        assertThatThrownBy(() -> MonitorSelection.requested(List.of(0L)))
                .isInstanceOf(InvalidReportRequestException.class);
        assertThatThrownBy(() -> MonitorSelection.requested(Arrays.asList(1L, null)))
                .isInstanceOf(InvalidReportRequestException.class);
        assertThatThrownBy(() -> new MonitorSelection(true, List.of(1L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fingerprintIgnoresRequestTimeAndIdOrder() {
        var first = new GenerationParameters(PERIOD, MonitorSelection.requested(List.of(11L, 12L)), REQUESTED);
        var retried = new GenerationParameters(PERIOD, MonitorSelection.requested(List.of(12L, 11L)),
                REQUESTED.plusSeconds(30));

        assertThat(first.fingerprint(ReportFormat.CSV))
                .hasSize(64)
                .isEqualTo(retried.fingerprint(ReportFormat.CSV));
    }

    @Test
    void fingerprintChangesWithAnythingThatChangesTheResult() {
        var base = new GenerationParameters(PERIOD, MonitorSelection.requested(List.of(11L)), REQUESTED);
        var otherPeriod = new GenerationParameters(new ReportPeriod(PERIOD.from(), PERIOD.to().plusSeconds(1)),
                base.selection(), REQUESTED);
        var allMonitors = new GenerationParameters(PERIOD, MonitorSelection.allMonitors(), REQUESTED);

        assertThat(List.of(base.fingerprint(ReportFormat.PDF), otherPeriod.fingerprint(ReportFormat.CSV),
                allMonitors.fingerprint(ReportFormat.CSV)))
                .doesNotContain(base.fingerprint(ReportFormat.CSV))
                .doesNotHaveDuplicates();
    }
}
