package org.awesoma.report.application.usecase;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.awesoma.report.application.port.AccessLevel;
import org.awesoma.report.application.port.IdempotencyKeys;
import org.awesoma.report.application.port.IdempotencyKeys.IdempotencyRecord;
import org.awesoma.report.application.port.ProjectAccessPort;
import org.awesoma.report.application.port.ProjectGuard;
import org.awesoma.report.application.port.ProjectNotFoundException;
import org.awesoma.report.application.port.ReportDataSource;
import org.awesoma.report.application.port.ReportRepository;
import org.awesoma.report.application.port.SourceRejectedException;
import org.awesoma.report.application.port.SourceUnavailableException;
import org.awesoma.report.application.port.TransactionRunner;
import org.awesoma.report.application.usecase.ReportUseCases.GenerateReport;
import org.awesoma.report.domain.GenerationParameters;
import org.awesoma.report.domain.InvalidReportRequestException;
import org.awesoma.report.domain.MonitorSelection;
import org.awesoma.report.domain.Report;
import org.awesoma.report.domain.ReportPeriod;

/**
 * Accepts a generation job. Everything that can be checked now is checked before anything is
 * stored; then the job and its Idempotency-Key are saved in one short transaction and the
 * caller gets 202 without waiting for the report. A worker builds it later.
 */
public class GenerationService implements GenerateReport {
    static final int MAX_KEY_LENGTH = 255;

    private final ReportRepository reports;
    private final IdempotencyKeys keys;
    private final ReportDataSource sources;
    private final ProjectAccessPort access;
    private final ProjectGuard projects;
    private final TransactionRunner transactions;
    private final Clock clock;
    private final GenerationLimits limits;

    public GenerationService(ReportRepository reports, IdempotencyKeys keys, ReportDataSource sources,
            ProjectAccessPort access, ProjectGuard projects, TransactionRunner transactions, Clock clock,
            GenerationLimits limits) {
        this.reports = reports;
        this.keys = keys;
        this.sources = sources;
        this.access = access;
        this.projects = projects;
        this.transactions = transactions;
        this.clock = clock;
        this.limits = limits;
    }

    @Override
    public Report generate(GenerateReportCommand command) {
        var now = clock.instant();
        if (command.format() == null) throw new InvalidReportRequestException("format", "Format is required");
        String key = command.idempotencyKey();
        if (key != null && (key.isBlank() || key.length() > MAX_KEY_LENGTH)) {
            throw new InvalidReportRequestException("Idempotency-Key",
                    "Idempotency-Key must have 1 to " + MAX_KEY_LENGTH + " characters");
        }
        var period = ReportPeriod.requested(command.from(), command.to(), now, limits.maxPeriod());
        var selection = MonitorSelection.requested(command.monitorIds());
        if (selection.size() > limits.maxMonitors()) {
            throw new InvalidReportRequestException("monitor_ids", "At most " + limits.maxMonitors() + " monitors");
        }
        ProjectAccessCheck.require(access, command.projectId(), command.userId(), AccessLevel.WRITE);
        var parameters = new GenerationParameters(period, selection, now);
        String fingerprint = parameters.fingerprint(command.format());
        // A retried request gets its job back even while the monitors cannot be checked.
        var known = transactions.inTransaction(() -> existing(command, fingerprint));
        if (known.isPresent()) return known.get();

        requireMonitors(command.projectId(), selection);
        return transactions.inTransaction(() -> accept(command, parameters, fingerprint))
                // A concurrent request bound the key first; its transaction has committed by now.
                .or(() -> transactions.inTransaction(() -> existing(command, fingerprint)))
                .orElseThrow(IdempotencyConflictException::otherParameters);
    }

    /** @return the new job, or nothing when another request bound the same key meanwhile */
    private Optional<Report> accept(GenerateReportCommand command, GenerationParameters parameters,
            String fingerprint) {
        projects.lock(command.projectId());
        if (projects.deleted(command.projectId())) {
            throw new ReportNotFoundException("Project " + command.projectId() + " not found");
        }
        var known = existing(command, fingerprint);
        if (known.isPresent()) return known;
        var report = Report.queueGeneration(UUID.randomUUID(), command.projectId(), command.userId(),
                command.format(), parameters);
        if (command.idempotencyKey() != null && !keys.bind(new IdempotencyRecord(command.userId(),
                command.projectId(), command.idempotencyKey(), fingerprint, report.id(), null))) {
            return Optional.empty();
        }
        return Optional.of(reports.save(report));
    }

    /** The job an Idempotency-Key already stands for, if the key is still bound. */
    private Optional<Report> existing(GenerateReportCommand command, String fingerprint) {
        String key = command.idempotencyKey();
        if (key == null) return Optional.empty();
        var record = keys.find(command.userId(), command.projectId(), key);
        if (record.isEmpty()) return Optional.empty();
        var now = clock.instant();
        if (record.get().expiredAt(now)) {
            keys.remove(command.userId(), command.projectId(), key);
            return Optional.empty();
        }
        if (record.get().tombstoneAt(now)) throw IdempotencyConflictException.reportDeleted();
        if (!record.get().fingerprint().equals(fingerprint)) throw IdempotencyConflictException.otherParameters();
        var report = reports.findById(record.get().reportId());
        if (report.isEmpty() || !report.get().visible()) throw IdempotencyConflictException.reportDeleted();
        return report;
    }

    /**
     * Explicit IDs must all belong to the project, and "all monitors" must find at least one;
     * a worker meeting the same problem later ends the job as FAILED instead.
     */
    private void requireMonitors(long projectId, MonitorSelection selection) {
        try {
            var first = sources.monitors(projectId, selection, null, 1);
            if (selection.all() && first.monitors().items().isEmpty()) {
                throw new InvalidReportRequestException("monitor_ids", "The project has no monitors");
            }
        } catch (SourceRejectedException foreign) {
            throw new InvalidReportRequestException("monitor_ids", "Every monitor must belong to the project");
        } catch (ProjectNotFoundException gone) {
            throw new ReportNotFoundException("Project " + projectId + " not found");
        } catch (SourceUnavailableException unavailable) {
            throw new ReportUnavailableException("Monitors of the project cannot be checked now", unavailable);
        }
    }
}
