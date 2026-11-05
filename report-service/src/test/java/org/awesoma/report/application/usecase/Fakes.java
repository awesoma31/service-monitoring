package org.awesoma.report.application.usecase;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.awesoma.report.application.port.AccessDecision;
import org.awesoma.report.application.port.AccessLevel;
import org.awesoma.report.application.port.EventInbox;
import org.awesoma.report.application.port.IdempotencyKeys;
import org.awesoma.report.application.port.MonitorPage;
import org.awesoma.report.application.port.ObjectNotFoundException;
import org.awesoma.report.application.port.ObjectStorage;
import org.awesoma.report.application.port.ObjectStorageUnavailableException;
import org.awesoma.report.application.port.ProjectAccessPort;
import org.awesoma.report.application.port.ProjectGuard;
import org.awesoma.report.application.port.ReportDataSource;
import org.awesoma.report.application.port.ReportJobQueue;
import org.awesoma.report.application.port.ReportPage;
import org.awesoma.report.application.port.ReportQuery;
import org.awesoma.report.application.port.ReportRepository;
import org.awesoma.report.application.port.SourcePage;
import org.awesoma.report.application.port.StoredKey;
import org.awesoma.report.application.port.StoredObject;
import org.awesoma.report.application.port.TransactionRunner;
import org.awesoma.report.domain.Lease;
import org.awesoma.report.domain.MonitorSelection;
import org.awesoma.report.domain.Report;
import org.awesoma.report.domain.ReportPeriod;
import org.awesoma.report.domain.ReportStatus;
import org.awesoma.report.domain.source.CheckRecord;
import org.awesoma.report.domain.source.IncidentRecord;
import org.awesoma.report.domain.source.MonitorInfo;
import org.awesoma.report.domain.source.ProjectInfo;

/** In-memory stand-ins for the ports, for use cases tested without infrastructure. */
final class Fakes {

    private Fakes() {
    }

    static final TransactionRunner TRANSACTIONS = new TransactionRunner() {
        @Override
        public <T> T inTransaction(Supplier<T> work) {
            return work.get();
        }
    };

    /** Keeps copies, as a database would: a change counts only once it is saved. */
    static final class Reports implements ReportRepository, ReportJobQueue {
        final Map<UUID, Report> rows = java.util.Collections.synchronizedMap(new LinkedHashMap<>());

        Report row(UUID id) {
            return rows.get(id);
        }

        @Override
        public Optional<Report> findById(UUID id) {
            return Optional.ofNullable(rows.get(id)).map(report -> Report.restore(report.snapshot()));
        }

        @Override
        public Optional<Report> lockById(UUID id) {
            return findById(id);
        }

        @Override
        public Report save(Report report) {
            rows.put(report.id(), Report.restore(report.snapshot()));
            return report;
        }

        @Override
        public void delete(UUID id) {
            rows.remove(id);
        }

        @Override
        public ReportPage findVisible(ReportQuery query) {
            var visible = rows.values().stream()
                    .filter(report -> report.projectId() == query.projectId() && report.visible()).toList();
            return new ReportPage(visible, visible.size(), false);
        }

        @Override
        public List<Report> lockVisibleOfProject(long projectId) {
            return rows.values().stream().filter(report -> report.projectId() == projectId && report.visible())
                    .map(report -> Report.restore(report.snapshot())).toList();
        }

        @Override
        public List<UUID> findDeleting(int limit) {
            return rows.values().stream().filter(report -> report.status() == ReportStatus.DELETING)
                    .map(Report::id).limit(limit).toList();
        }

        @Override
        public Optional<Report> claimNext(Lease lease, Instant now) {
            return rows.values().stream().filter(report -> report.dueAt(now))
                    .min(Comparator.comparing(Report::createdAt))
                    .map(report -> {
                        var claimed = Report.restore(report.snapshot());
                        claimed.startAttempt(lease, now);
                        return save(claimed);
                    });
        }

        @Override
        public List<UUID> expiredLeases(Instant now, int limit) {
            return rows.values().stream().filter(report -> report.status() == ReportStatus.GENERATING
                    && !report.lease().heldAt(now)).map(Report::id).limit(limit).toList();
        }

        @Override
        public List<UUID> expiredUploads(Instant now, int limit) {
            return rows.values().stream().filter(report -> report.status() == ReportStatus.UPLOADING
                    && report.lease() != null && !report.lease().heldAt(now)).map(Report::id).limit(limit).toList();
        }
    }

    static final class Storage implements ObjectStorage {
        final Map<String, byte[]> objects = new LinkedHashMap<>();
        final Map<String, Instant> written = new HashMap<>();
        final List<String> deleted = new ArrayList<>();
        boolean putFails;
        boolean openFails;
        boolean deleteFails;
        Instant now = Instant.parse("2026-11-02T12:00:00Z");
        Runnable onPut = () -> { };

        @Override
        public void put(String key, InputStream content, long size, String contentType) {
            if (putFails) throw new ObjectStorageUnavailableException("down", null);
            try {
                objects.put(key, content.readAllBytes());
                written.put(key, now);
            } catch (IOException error) {
                throw new UncheckedIOException(error);
            }
            onPut.run();
        }

        @Override
        public StoredObject open(String key) {
            if (openFails) throw new ObjectStorageUnavailableException("down", null);
            byte[] content = objects.get(key);
            if (content == null) throw new ObjectNotFoundException(key);
            return new StoredObject(new ByteArrayInputStream(content), content.length);
        }

        @Override
        public void delete(String key) {
            if (deleteFails) throw new ObjectStorageUnavailableException("down", null);
            deleted.add(key);
            objects.remove(key);
        }

        @Override
        public boolean exists(String key) {
            return objects.containsKey(key);
        }

        @Override
        public List<StoredKey> list(String prefix) {
            if (deleteFails) throw new ObjectStorageUnavailableException("down", null);
            return objects.keySet().stream().filter(key -> key.startsWith(prefix))
                    .map(key -> new StoredKey(key, written.get(key))).toList();
        }
    }

    static final class Keys implements IdempotencyKeys {
        final Map<String, IdempotencyRecord> records = new HashMap<>();

        private static String id(long userId, long projectId, String key) {
            return userId + "/" + projectId + "/" + key;
        }

        @Override
        public Optional<IdempotencyRecord> find(long userId, long projectId, String key) {
            return Optional.ofNullable(records.get(id(userId, projectId, key)));
        }

        @Override
        public boolean bind(IdempotencyRecord record) {
            return records.putIfAbsent(id(record.userId(), record.projectId(), record.key()), record) == null;
        }

        @Override
        public boolean removeExpired(IdempotencyRecord observed, Instant now) {
            return observed.expiredAt(now) && records.remove(id(observed.userId(), observed.projectId(),
                    observed.key()), observed);
        }

        @Override
        public void tombstone(UUID reportId, Instant expiresAt) {
            records.replaceAll((id, record) -> record.reportId().equals(reportId)
                    ? new IdempotencyRecord(record.userId(), record.projectId(), record.key(), record.fingerprint(),
                            reportId, expiresAt)
                    : record);
        }

        @Override
        public int removeExpired(Instant now) {
            int before = records.size();
            records.values().removeIf(record -> record.expiredAt(now));
            return before - records.size();
        }
    }

    static final class Projects implements ProjectGuard, EventInbox {
        final Set<Long> deleted = new HashSet<>();
        final Set<UUID> events = new HashSet<>();
        final List<Long> locked = new ArrayList<>();

        @Override
        public void lock(long projectId) {
            locked.add(projectId);
        }

        @Override
        public boolean deleted(long projectId) {
            return deleted.contains(projectId);
        }

        @Override
        public void markDeleted(long projectId) {
            deleted.add(projectId);
        }

        @Override
        public boolean record(UUID eventId) {
            return events.add(eventId);
        }
    }

    static final class Access implements ProjectAccessPort {
        AccessDecision decision = AccessDecision.ALLOWED;
        RuntimeException failure;

        @Override
        public AccessDecision check(long projectId, long userId, AccessLevel level) {
            if (failure != null) throw failure;
            return decision;
        }
    }

    /**
     * Serves fixed monitors, checks and incidents in pages of the requested size. A failure can
     * be set for the n-th call of a kind, to see how an attempt recovers.
     */
    static final class Sources implements ReportDataSource {
        ProjectInfo project = new ProjectInfo(42, "Сервисы");
        List<MonitorInfo> monitors = new ArrayList<>(List.of(new MonitorInfo(11, "Сайт", "https://a.example"),
                new MonitorInfo(12, "API", "https://b.example")));
        List<CheckRecord> checks = new ArrayList<>();
        List<IncidentRecord> incidents = new ArrayList<>();
        final Map<String, Integer> calls = new HashMap<>();
        final Map<String, Supplier<RuntimeException>> failOnCall = new HashMap<>();
        Instant capturedAt = Instant.parse("2026-11-02T12:00:00Z");

        @Override
        public MonitorPage monitors(long projectId, MonitorSelection selection, String cursor, int limit) {
            fail("monitors");
            var selected = selection.all() ? monitors
                    : monitors.stream().filter(monitor -> selection.monitorIds().contains(monitor.id())).toList();
            return new MonitorPage(project, page(selected, cursor, limit, null));
        }

        @Override
        public SourcePage<CheckRecord> checks(long projectId, List<Long> monitorIds, ReportPeriod period,
                String cursor, int limit) {
            fail("checks");
            return page(checks, cursor, limit, checks.stream().mapToLong(CheckRecord::id).max().orElse(0));
        }

        @Override
        public SourcePage<IncidentRecord> incidents(long projectId, List<Long> monitorIds, ReportPeriod period,
                String cursor, int limit) {
            fail("incidents");
            return page(incidents, cursor, limit, incidents.stream().mapToLong(IncidentRecord::id).max().orElse(0));
        }

        private void fail(String kind) {
            int call = calls.merge(kind, 1, Integer::sum);
            var failure = failOnCall.remove(kind + "#" + call);
            var error = failure == null ? null : failure.get();
            if (error != null) throw error;
        }

        private <T> SourcePage<T> page(List<T> all, String cursor, int limit, Long upper) {
            int from = cursor == null ? 0 : Integer.parseInt(cursor);
            int to = Math.min(all.size(), from + limit);
            boolean next = to < all.size();
            return new SourcePage<>(all.subList(from, to), next ? Integer.toString(to) : null, next, capturedAt,
                    upper);
        }
    }
}
