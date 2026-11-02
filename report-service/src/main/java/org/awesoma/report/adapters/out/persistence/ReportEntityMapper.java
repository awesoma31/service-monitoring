package org.awesoma.report.adapters.out.persistence;

import java.util.Arrays;
import org.awesoma.report.domain.FileName;
import org.awesoma.report.domain.GenerationParameters;
import org.awesoma.report.domain.Lease;
import org.awesoma.report.domain.MonitorSelection;
import org.awesoma.report.domain.ReadBounds;
import org.awesoma.report.domain.Report;
import org.awesoma.report.domain.ReportPeriod;
import org.awesoma.report.domain.ReportSnapshot;

/** Copies a report to and from its row through the snapshot, so no rule is bypassed. */
final class ReportEntityMapper {

    private ReportEntityMapper() {
    }

    static ReportEntity toEntity(Report report) {
        ReportSnapshot s = report.snapshot();
        var entity = new ReportEntity();
        entity.setId(s.id());
        entity.setProjectId(s.projectId());
        entity.setCreatedBy(s.createdBy());
        entity.setSource(s.source());
        entity.setFormat(s.format());
        entity.setFileName(s.fileName().value());
        entity.setStatus(s.status());
        entity.setCreatedAt(s.createdAt());
        entity.setUpdatedAt(s.updatedAt());
        entity.setObjectKey(s.objectKey());
        entity.setSizeBytes(s.sizeBytes());
        entity.setSha256(s.sha256());
        entity.setFailureCode(s.failure());
        entity.setAttempts(s.attempts());
        entity.setNextAttemptAt(s.nextAttemptAt());
        entity.setGeneratedAt(s.generatedAt());
        if (s.generation() != null) {
            entity.setPeriodFrom(s.generation().period().from());
            entity.setPeriodTo(s.generation().period().to());
            entity.setAllMonitors(s.generation().selection().all());
            entity.setMonitorIds(s.generation().selection().all() ? null
                    : s.generation().selection().monitorIds().toArray(Long[]::new));
            entity.setRequestedAt(s.generation().requestedAt());
        }
        if (s.lease() != null) {
            entity.setLeaseAttemptId(s.lease().attemptId());
            entity.setLeaseToken(s.lease().token());
            entity.setLeaseUntil(s.lease().until());
        }
        if (s.readBounds() != null) {
            entity.setBoundsObservedAt(s.readBounds().observedAt());
            entity.setChecksUpperId(s.readBounds().checksUpperId());
            entity.setIncidentsUpperId(s.readBounds().incidentsUpperId());
        }
        return entity;
    }

    static Report toDomain(ReportEntity entity) {
        GenerationParameters generation = entity.getPeriodFrom() == null ? null : new GenerationParameters(
                new ReportPeriod(entity.getPeriodFrom(), entity.getPeriodTo()),
                Boolean.TRUE.equals(entity.getAllMonitors()) ? MonitorSelection.allMonitors()
                        : MonitorSelection.requested(Arrays.asList(entity.getMonitorIds())),
                entity.getRequestedAt());
        Lease lease = entity.getLeaseToken() == null ? null
                : new Lease(entity.getLeaseAttemptId(), entity.getLeaseToken(), entity.getLeaseUntil());
        ReadBounds bounds = entity.getBoundsObservedAt() == null ? null : new ReadBounds(
                entity.getBoundsObservedAt(), entity.getChecksUpperId(), entity.getIncidentsUpperId());
        return Report.restore(new ReportSnapshot(entity.getId(), entity.getProjectId(), entity.getCreatedBy(),
                entity.getSource(), entity.getFormat(), new FileName(entity.getFileName()), entity.getStatus(),
                entity.getCreatedAt(), entity.getUpdatedAt(), entity.getObjectKey(), entity.getSizeBytes(),
                entity.getSha256(), entity.getFailureCode(), generation, lease, entity.getAttempts(),
                entity.getNextAttemptAt(), entity.getGeneratedAt(), bounds));
    }
}
