package org.awesoma.report.application.usecase;

import java.time.Clock;
import java.util.UUID;
import org.awesoma.report.application.content.ContentInspector;
import org.awesoma.report.application.content.ContentRejectedException;
import org.awesoma.report.application.content.ContentTooLargeException;
import org.awesoma.report.application.content.DeclaredContentType;
import org.awesoma.report.application.content.InvalidContentException;
import org.awesoma.report.application.content.MeasuredInputStream;
import org.awesoma.report.application.content.UnsupportedContentTypeException;
import org.awesoma.report.application.port.AccessLevel;
import org.awesoma.report.application.port.ObjectNotFoundException;
import org.awesoma.report.application.port.ObjectStorage;
import org.awesoma.report.application.port.ObjectStorageUnavailableException;
import org.awesoma.report.application.port.ProjectAccessPort;
import org.awesoma.report.application.port.ProjectGuard;
import org.awesoma.report.application.port.ReportPage;
import org.awesoma.report.application.port.ReportQuery;
import org.awesoma.report.application.port.ReportRepository;
import org.awesoma.report.application.port.TransactionRunner;
import org.awesoma.report.application.usecase.ReportUseCases.DeleteReport;
import org.awesoma.report.application.usecase.ReportUseCases.DownloadReport;
import org.awesoma.report.application.usecase.ReportUseCases.GetReportMetadata;
import org.awesoma.report.application.usecase.ReportUseCases.ListReports;
import org.awesoma.report.application.usecase.ReportUseCases.UploadReport;
import org.awesoma.report.domain.FileName;
import org.awesoma.report.domain.InvalidReportRequestException;
import org.awesoma.report.domain.Lease;
import org.awesoma.report.domain.Report;
import org.awesoma.report.domain.ReportFormat;
import org.awesoma.report.domain.ReportStatus;
import org.awesoma.report.domain.StaleAttemptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stored reports: upload, read, download and delete. Database and storage share no
 * transaction, so a file is stored between two short transactions, and a report becomes
 * READY only after the storage confirmed the object. Both transactions hold the project's
 * lock and check that the project was not deleted meanwhile. Every operation first checks
 * the caller's right to the project; nothing about a report is revealed before that.
 */
public class ReportService implements UploadReport, GetReportMetadata, DownloadReport, ListReports,
        DeleteReport {
    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final ReportRepository reports;
    private final ObjectStorage storage;
    private final ProjectAccessPort access;
    private final ProjectGuard projects;
    private final ReportCleanup cleanup;
    private final TransactionRunner transactions;
    private final Clock clock;
    private final ReportLimits limits;

    public ReportService(ReportRepository reports, ObjectStorage storage, ProjectAccessPort access,
            ProjectGuard projects, ReportCleanup cleanup, TransactionRunner transactions, Clock clock,
            ReportLimits limits) {
        this.reports = reports;
        this.storage = storage;
        this.access = access;
        this.projects = projects;
        this.cleanup = cleanup;
        this.transactions = transactions;
        this.clock = clock;
        this.limits = limits;
    }

    @Override
    public Report upload(UploadReportCommand command) {
        ProjectAccessCheck.require(access, command.projectId(), command.userId(), AccessLevel.WRITE);
        FileName name = FileName.fromClient(command.fileName());
        ReportFormat format = ReportFormat.fromExtension(name.extension()).orElseThrow(() ->
                new UnsupportedContentTypeException("Only .csv and .pdf files are accepted"));
        DeclaredContentType.requireCompatible(command.declaredContentType(), format);
        if (command.declaredSize() == 0) throw new InvalidContentException("File is empty");
        if (command.declaredSize() > limits.maxFileSize()) throw new ContentTooLargeException(limits.maxFileSize());

        var now = clock.instant();
        var lease = new Lease(UUID.randomUUID(), UUID.randomUUID(), now.plus(limits.uploadTimeout()));
        var report = transactions.inTransaction(() -> {
            projects.lock(command.projectId());
            if (projects.deleted(command.projectId())) throw projectGone(command.projectId());
            return reports.save(Report.startUpload(UUID.randomUUID(), command.projectId(), command.userId(), name,
                    lease, now));
        });
        try {
            var inspected = ContentInspector.of(command.content(), format);
            var measured = new MeasuredInputStream(inspected, limits.maxFileSize());
            storage.put(report.objectKey(), measured, command.declaredSize(), format.contentType());
            inspected.verifyComplete();
            var published = transactions.inTransaction(() -> {
                projects.lock(report.projectId());
                var current = reports.lockById(report.id()).orElseThrow(() -> stale(report));
                if (projects.deleted(report.projectId())) {
                    current.markDeleting(clock.instant());
                } else {
                    current.completeUpload(lease.token(), measured.size(), measured.sha256(), clock.instant());
                }
                return reports.save(current);
            });
            if (!published.contentAvailable()) {
                cleanup.purge(published);
                throw projectGone(report.projectId());
            }
            return published;
        } catch (ContentRejectedException rejected) {
            discard(report);
            throw rejected;
        } catch (ObjectStorageUnavailableException | StaleAttemptException error) {
            fail(report, lease);
            throw new ReportUnavailableException("The file could not be stored, try again later", error);
        }
    }

    @Override
    public Report get(UUID reportId, long userId) {
        return visible(reportId, userId, AccessLevel.READ);
    }

    @Override
    public ReportDownload download(UUID reportId, long userId) {
        var report = visible(reportId, userId, AccessLevel.READ);
        if (!report.contentAvailable()) throw new ReportNotReadyException(report.status());
        try {
            return new ReportDownload(report, storage.open(report.objectKey()));
        } catch (ObjectNotFoundException missing) {
            log.error("Object of a ready report is missing: report_id={}, project_id={}",
                    report.id(), report.projectId());
            transactions.inTransaction(() -> reports.lockById(reportId)
                    .filter(current -> current.status() == ReportStatus.READY)
                    .ifPresent(current -> {
                        current.markObjectMissing(clock.instant());
                        reports.save(current);
                    }));
            throw new ReportUnavailableException("Report content is missing", missing);
        } catch (ObjectStorageUnavailableException unavailable) {
            throw new ReportUnavailableException("Report content is temporarily unavailable", unavailable);
        }
    }

    @Override
    public ReportPage list(ListReportsQuery query) {
        if (query.page() < 0) throw new InvalidReportRequestException("page", "Page must not be negative");
        if (query.size() < 1 || query.size() > limits.maxPageSize()) {
            throw new InvalidReportRequestException("size", "Size must be between 1 and " + limits.maxPageSize());
        }
        ProjectAccessCheck.require(access, query.projectId(), query.userId(), AccessLevel.READ);
        return reports.findVisible(new ReportQuery(query.projectId(), query.status(), query.source(),
                query.page(), query.size()));
    }

    /**
     * Hides the report at once and revokes any running attempt; the objects are removed after
     * the commit. If that fails, the report stays DELETING for the background cleanup.
     */
    @Override
    public void delete(UUID reportId, long userId) {
        var report = reports.findById(reportId).orElseThrow(() -> notFound(reportId));
        ProjectAccessCheck.require(access, report.projectId(), userId, AccessLevel.WRITE);
        transactions.inTransaction(() -> {
            var current = reports.lockById(reportId).orElseThrow(() -> notFound(reportId));
            if (current.markDeleting(clock.instant())) reports.save(current);
        });
        cleanup.purge(report);
    }

    private Report visible(UUID reportId, long userId, AccessLevel level) {
        var report = reports.findById(reportId).orElseThrow(() -> notFound(reportId));
        ProjectAccessCheck.require(access, report.projectId(), userId, level);
        if (!report.visible()) throw notFound(reportId);
        return report;
    }

    /** Rejected content is not kept: the report is hidden and its object removed. */
    private void discard(Report report) {
        transactions.inTransaction(() -> reports.lockById(report.id()).ifPresent(current -> {
            if (current.markDeleting(clock.instant())) reports.save(current);
        }));
        cleanup.purge(report);
    }

    /** A storage failure leaves a visible FAILED report; its object is removed if there is one. */
    private void fail(Report report, Lease lease) {
        try {
            transactions.inTransaction(() -> reports.lockById(report.id()).ifPresent(current -> {
                current.failUpload(lease.token(), clock.instant());
                reports.save(current);
            }));
        } catch (StaleAttemptException alreadyChanged) {
            log.info("Upload changed state before it could be marked failed: report_id={}", report.id());
        }
        cleanup.removeObject(report.objectKey());
    }

    private static ReportNotFoundException notFound(UUID reportId) {
        return new ReportNotFoundException("Report " + reportId + " not found");
    }

    private static ReportNotFoundException projectGone(long projectId) {
        return new ReportNotFoundException("Project " + projectId + " not found");
    }

    private static StaleAttemptException stale(Report report) {
        return new StaleAttemptException("Report " + report.id() + " no longer exists");
    }
}
