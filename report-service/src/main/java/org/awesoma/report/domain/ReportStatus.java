package org.awesoma.report.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * <pre>
 * UPLOADED:  UPLOADING ──> READY ──> DELETING
 *                  └─────> FAILED ──> DELETING
 * GENERATED: QUEUED ──> GENERATING ──> READY ──> DELETING
 *               ↑          ├────────> FAILED ──> DELETING
 *               └──────────┘ temporary failure: retry after a delay
 * Any visible state ── deletion of the report or its project ──> DELETING
 * </pre>
 * READY may also become FAILED when the storage confirms that its object is gone.
 */
public enum ReportStatus {
    UPLOADING,
    QUEUED,
    GENERATING,
    READY,
    FAILED,
    DELETING;

    private Set<ReportStatus> next() {
        return switch (this) {
            case UPLOADING -> EnumSet.of(READY, FAILED, DELETING);
            case QUEUED -> EnumSet.of(GENERATING, DELETING);
            case GENERATING -> EnumSet.of(READY, FAILED, QUEUED, DELETING);
            case READY -> EnumSet.of(FAILED, DELETING);
            case FAILED -> EnumSet.of(DELETING);
            case DELETING -> EnumSet.noneOf(ReportStatus.class);
        };
    }

    public boolean canMoveTo(ReportStatus target) {
        return next().contains(target);
    }

    /** Reports being deleted are hidden from lists and reads. */
    public boolean visible() {
        return this != DELETING;
    }
}
