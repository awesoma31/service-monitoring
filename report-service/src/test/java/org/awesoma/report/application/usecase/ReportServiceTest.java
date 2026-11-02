package org.awesoma.report.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.awesoma.report.application.content.ContentTooLargeException;
import org.awesoma.report.application.content.InvalidContentException;
import org.awesoma.report.application.content.UnsupportedContentTypeException;
import org.awesoma.report.application.port.AccessDecision;
import org.awesoma.report.application.port.AccessUnavailableException;
import org.awesoma.report.application.port.ObjectNotFoundException;
import org.awesoma.report.application.port.ObjectStorage;
import org.awesoma.report.application.port.ObjectStorageUnavailableException;
import org.awesoma.report.application.port.ReportPage;
import org.awesoma.report.application.port.ReportQuery;
import org.awesoma.report.application.port.ReportRepository;
import org.awesoma.report.application.port.StoredObject;
import org.awesoma.report.application.port.TransactionRunner;
import org.awesoma.report.domain.FailureCode;
import org.awesoma.report.domain.Report;
import org.awesoma.report.domain.ReportStatus;
import org.junit.jupiter.api.Test;

/** Failure paths that need precise control over storage and time. */
class ReportServiceTest {
    private static final byte[] CSV = "a,b\n1,2\n".getBytes(StandardCharsets.UTF_8);
    private static final ReportLimits LIMITS = new ReportLimits(1024, Duration.ofMinutes(10), 50);

    private final InMemoryReports reports = new InMemoryReports();
    private final FakeStorage storage = new FakeStorage();
    private AccessDecision decision = AccessDecision.ALLOWED;
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-02T12:00:00Z"));
    private final TransactionRunner transactions = new TransactionRunner() {
        @Override
        public <T> T inTransaction(Supplier<T> work) {
            return work.get();
        }
    };

    private ReportService service() {
        return new ReportService(reports, storage, (project, user, level) -> decision, transactions,
                clock, LIMITS);
    }

    @Test
    void storesTheFileAndPublishesItsHash() {
        var report = service().upload(command("uptime.csv", CSV.length, CSV));

        assertThat(report.status()).isEqualTo(ReportStatus.READY);
        assertThat(report.sizeBytes()).isEqualTo(CSV.length);
        assertThat(storage.objects).containsKey(report.objectKey());
        assertThat(reports.rows.get(report.id()).status()).isEqualTo(ReportStatus.READY);
    }

    @Test
    void refusesBeforeStoringAnything() {
        assertThatThrownBy(() -> service().upload(command("uptime.csv", 2048, CSV)))
                .isInstanceOf(ContentTooLargeException.class);
        assertThatThrownBy(() -> service().upload(command("uptime.csv", 0, new byte[0])))
                .isInstanceOf(InvalidContentException.class);
        assertThatThrownBy(() -> service().upload(command("uptime.xlsx", CSV.length, CSV)))
                .isInstanceOf(UnsupportedContentTypeException.class);
        decision = AccessDecision.DENIED;
        assertThatThrownBy(() -> service().upload(command("uptime.csv", CSV.length, CSV)))
                .isInstanceOf(ProjectAccessDeniedException.class);

        assertThat(reports.rows).isEmpty();
        assertThat(storage.objects).isEmpty();
    }

    @Test
    void aStreamLongerThanTheLimitIsCutOffAndForgotten() {
        byte[] big = new byte[2048];
        Arrays.fill(big, (byte) 'a');

        assertThatThrownBy(() -> service().upload(command("big.csv", -1, big)))
                .isInstanceOf(ContentTooLargeException.class);

        assertThat(reports.rows).isEmpty();
        assertThat(storage.objects).isEmpty();
    }

    @Test
    void aStorageOutageLeavesAVisibleFailedUpload() {
        storage.putFails = true;

        assertThatThrownBy(() -> service().upload(command("uptime.csv", CSV.length, CSV)))
                .isInstanceOf(ReportUnavailableException.class);

        var failed = reports.rows.values().iterator().next();
        assertThat(failed.status()).isEqualTo(ReportStatus.FAILED);
        assertThat(failed.failure()).isEqualTo(FailureCode.UPLOAD_FAILED);
        assertThat(storage.deleted).contains(failed.objectKey());
    }

    @Test
    void anUploadThatOutlivesItsLeaseIsNotPublished() {
        storage.onPut = () -> clock.advance(Duration.ofMinutes(11));

        assertThatThrownBy(() -> service().upload(command("uptime.csv", CSV.length, CSV)))
                .isInstanceOf(ReportUnavailableException.class);

        assertThat(reports.rows.values()).extracting(Report::status).containsExactly(ReportStatus.FAILED);
        assertThat(storage.objects).isEmpty();
    }

    @Test
    void aReportDeletedDuringItsUploadStaysDeleted() {
        storage.onPut = () -> reports.rows.values().forEach(report -> report.markDeleting(clock.instant()));

        assertThatThrownBy(() -> service().upload(command("uptime.csv", CSV.length, CSV)))
                .isInstanceOf(ReportUnavailableException.class);

        assertThat(reports.rows.values()).extracting(Report::status).containsExactly(ReportStatus.DELETING);
        assertThat(storage.objects).isEmpty();
    }

    @Test
    void aDeletionWhoseObjectCannotBeRemovedWaitsForCleanup() {
        var report = service().upload(command("uptime.csv", CSV.length, CSV));
        storage.deleteFails = true;

        service().delete(report.id(), 7);

        assertThat(reports.rows.get(report.id()).status()).isEqualTo(ReportStatus.DELETING);
        assertThatThrownBy(() -> service().get(report.id(), 7)).isInstanceOf(ReportNotFoundException.class);

        storage.deleteFails = false;
        service().delete(report.id(), 7);
        assertThat(reports.rows).doesNotContainKey(report.id());
    }

    @Test
    void aStorageOutageDuringDownloadDoesNotChangeTheReport() {
        var report = service().upload(command("uptime.csv", CSV.length, CSV));
        storage.openFails = true;

        assertThatThrownBy(() -> service().download(report.id(), 7)).isInstanceOf(ReportUnavailableException.class);

        assertThat(reports.rows.get(report.id()).status()).isEqualTo(ReportStatus.READY);
    }

    @Test
    void anUnavailableAccessCheckIsNotSkipped() {
        var report = service().upload(command("uptime.csv", CSV.length, CSV));
        var failing = new ReportService(reports, storage, (project, user, level) -> {
            throw new AccessUnavailableException("down", null);
        }, transactions, clock, LIMITS);

        assertThatThrownBy(() -> failing.get(report.id(), 7)).isInstanceOf(AccessUnavailableException.class);
        assertThatThrownBy(() -> failing.download(report.id(), 7)).isInstanceOf(AccessUnavailableException.class);
    }

    @Test
    void validatesPagingBeforeAskingForAccess() {
        decision = AccessDecision.PROJECT_NOT_FOUND;

        assertThatThrownBy(() -> service().list(new ListReportsQuery(1, 7, null, null, 0, 0)))
                .hasMessageContaining("between 1 and 50");
        assertThatThrownBy(() -> service().list(new ListReportsQuery(1, 7, null, null, 0, 20)))
                .isInstanceOf(ReportNotFoundException.class);
    }

    private static UploadReportCommand command(String name, long size, byte[] content) {
        return new UploadReportCommand(42, 7, name, null, size, new ByteArrayInputStream(content));
    }

    private static final class InMemoryReports implements ReportRepository {
        final Map<UUID, Report> rows = new HashMap<>();

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
            return new ReportPage(rows.values().stream().filter(Report::visible).toList(), rows.size(), false);
        }
    }

    private static final class FakeStorage implements ObjectStorage {
        final Map<String, byte[]> objects = new HashMap<>();
        final List<String> deleted = new ArrayList<>();
        boolean putFails;
        boolean openFails;
        boolean deleteFails;
        Runnable onPut = () -> { };

        @Override
        public void put(String key, InputStream content, long size, String contentType) {
            if (putFails) throw new ObjectStorageUnavailableException("down", null);
            try {
                objects.put(key, content.readAllBytes());
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
    }
}
