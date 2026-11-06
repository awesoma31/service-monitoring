package org.awesoma.report.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awesoma.report.domain.ReportStatus.DELETING;
import static org.awesoma.report.domain.ReportStatus.FAILED;
import static org.awesoma.report.domain.ReportStatus.GENERATING;
import static org.awesoma.report.domain.ReportStatus.QUEUED;
import static org.awesoma.report.domain.ReportStatus.READY;
import static org.awesoma.report.domain.ReportStatus.UPLOADING;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReportStatusTest {
    private static final Map<ReportStatus, Set<ReportStatus>> ALLOWED = Map.of(
            UPLOADING, EnumSet.of(READY, FAILED, DELETING),
            QUEUED, EnumSet.of(GENERATING, DELETING),
            GENERATING, EnumSet.of(READY, FAILED, QUEUED, DELETING),
            READY, EnumSet.of(FAILED, DELETING),
            FAILED, EnumSet.of(DELETING),
            DELETING, EnumSet.noneOf(ReportStatus.class));

    @Test
    void allowsExactlyTheDocumentedTransitions() {
        for (ReportStatus from : ReportStatus.values()) {
            for (ReportStatus to : ReportStatus.values()) {
                assertThat(from.canMoveTo(to))
                        .as("%s -> %s", from, to)
                        .isEqualTo(ALLOWED.get(from).contains(to));
            }
        }
    }

    @Test
    void everyStateButDeletingIsVisible() {
        assertThat(EnumSet.allOf(ReportStatus.class)).filteredOn(ReportStatus::visible)
                .containsExactlyInAnyOrder(UPLOADING, QUEUED, GENERATING, READY, FAILED);
    }

    @Test
    void everyVisibleStateCanBeDeleted() {
        assertThat(EnumSet.allOf(ReportStatus.class)).filteredOn(ReportStatus::visible)
                .allMatch(status -> status.canMoveTo(DELETING));
    }
}
