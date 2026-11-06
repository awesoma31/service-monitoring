package org.awesoma.report.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import org.awesoma.report.application.content.ContentTooLargeException;
import org.awesoma.report.application.content.InvalidContentException;
import org.awesoma.report.application.content.UnsupportedContentTypeException;
import org.awesoma.report.application.port.AccessDecision;
import org.awesoma.report.application.port.AccessUnavailableException;
import org.awesoma.report.domain.FailureCode;
import org.awesoma.report.domain.Report;
import org.awesoma.report.domain.ReportStatus;
import org.junit.jupiter.api.Test;

/** Failure paths that need precise control over storage and time. */
class ReportServiceTest {
    private static final byte[] CSV = "a,b\n1,2\n".getBytes(StandardCharsets.UTF_8);
    private static final ReportLimits LIMITS = new ReportLimits(1024, Duration.ofMinutes(10), 50);

    private final Fakes.Reports reports = new Fakes.Reports();
    private final Fakes.Storage storage = new Fakes.Storage();
    private final Fakes.Keys keys = new Fakes.Keys();
    private final Fakes.Projects projects = new Fakes.Projects();
    private final Fakes.Access access = new Fakes.Access();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-02T12:00:00Z"));
    private final ReportCleanup cleanup = new ReportCleanup(reports, storage, keys, Fakes.TRANSACTIONS, clock,
            Duration.ofHours(24));
    private final Fakes.Events events = new Fakes.Events();
    private final ReportService service = new ReportService(reports, storage, access, projects, cleanup, events,
            Fakes.TRANSACTIONS, clock, LIMITS);

    @Test
    void announcesASuccessfulUploadOnce() {
        var report = service.upload(command("uptime.csv", CSV.length, CSV));

        assertThat(events.uploaded).containsExactly(report.id());
    }

    @Test
    void announcesNothingWhenTheStorageFails() {
        storage.putFails = true;

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.upload(command("uptime.csv", CSV.length, CSV)))
                .isInstanceOf(ReportUnavailableException.class);

        assertThat(events.uploaded).isEmpty();
    }

    @Test
    void announcesNothingWhenTheProjectIsDeletedDuringTheUpload() {
        storage.onPut = () -> projects.markDeleted(42);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.upload(command("uptime.csv", CSV.length, CSV)))
                .isInstanceOf(ReportNotFoundException.class);

        assertThat(events.uploaded).isEmpty();
    }

    @Test
    void storesTheFileAndPublishesItsHash() {
        var report = service.upload(command("uptime.csv", CSV.length, CSV));

        assertThat(report.status()).isEqualTo(ReportStatus.READY);
        assertThat(report.sizeBytes()).isEqualTo(CSV.length);
        assertThat(storage.objects).containsKey(report.objectKey());
        assertThat(reports.row(report.id()).status()).isEqualTo(ReportStatus.READY);
        assertThat(projects.locked).containsExactly(42L, 42L);
    }

    @Test
    void refusesBeforeStoringAnything() {
        assertThatThrownBy(() -> service.upload(command("uptime.csv", 2048, CSV)))
                .isInstanceOf(ContentTooLargeException.class);
        assertThatThrownBy(() -> service.upload(command("uptime.csv", 0, new byte[0])))
                .isInstanceOf(InvalidContentException.class);
        assertThatThrownBy(() -> service.upload(command("uptime.xlsx", CSV.length, CSV)))
                .isInstanceOf(UnsupportedContentTypeException.class);
        access.decision = AccessDecision.DENIED;
        assertThatThrownBy(() -> service.upload(command("uptime.csv", CSV.length, CSV)))
                .isInstanceOf(ProjectAccessDeniedException.class);

        assertThat(reports.rows).isEmpty();
        assertThat(storage.objects).isEmpty();
    }

    @Test
    void aStreamLongerThanTheLimitIsCutOffAndForgotten() {
        byte[] big = new byte[2048];
        Arrays.fill(big, (byte) 'a');

        assertThatThrownBy(() -> service.upload(command("big.csv", -1, big)))
                .isInstanceOf(ContentTooLargeException.class);

        assertThat(reports.rows).isEmpty();
        assertThat(storage.objects).isEmpty();
    }

    @Test
    void aStorageOutageLeavesAVisibleFailedUpload() {
        storage.putFails = true;

        assertThatThrownBy(() -> service.upload(command("uptime.csv", CSV.length, CSV)))
                .isInstanceOf(ReportUnavailableException.class);

        var failed = reports.rows.values().iterator().next();
        assertThat(failed.status()).isEqualTo(ReportStatus.FAILED);
        assertThat(failed.failure()).isEqualTo(FailureCode.UPLOAD_FAILED);
        assertThat(storage.deleted).contains(failed.objectKey());
    }

    @Test
    void anUploadThatOutlivesItsLeaseIsNotPublished() {
        storage.onPut = () -> clock.advance(Duration.ofMinutes(11));

        assertThatThrownBy(() -> service.upload(command("uptime.csv", CSV.length, CSV)))
                .isInstanceOf(ReportUnavailableException.class);

        assertThat(reports.rows.values()).extracting(Report::status).containsExactly(ReportStatus.FAILED);
        assertThat(storage.objects).isEmpty();
    }

    @Test
    void aReportDeletedDuringItsUploadStaysDeleted() {
        storage.onPut = () -> reports.rows.values().forEach(report -> report.markDeleting(clock.instant()));

        assertThatThrownBy(() -> service.upload(command("uptime.csv", CSV.length, CSV)))
                .isInstanceOf(ReportUnavailableException.class);

        assertThat(reports.rows.values()).extracting(Report::status).containsExactly(ReportStatus.DELETING);
        assertThat(storage.objects).isEmpty();
    }

    @Test
    void aProjectDeletedDuringTheUploadGetsNoReport() {
        storage.onPut = () -> projects.markDeleted(42);

        assertThatThrownBy(() -> service.upload(command("uptime.csv", CSV.length, CSV)))
                .isInstanceOf(ReportNotFoundException.class);

        assertThat(reports.rows).isEmpty();
        assertThat(storage.objects).isEmpty();
        assertThatThrownBy(() -> service.upload(command("uptime.csv", CSV.length, CSV)))
                .isInstanceOf(ReportNotFoundException.class);
    }

    @Test
    void aDeletionWhoseObjectCannotBeRemovedWaitsForCleanup() {
        var report = service.upload(command("uptime.csv", CSV.length, CSV));
        storage.deleteFails = true;

        service.delete(report.id(), 7);

        assertThat(reports.row(report.id()).status()).isEqualTo(ReportStatus.DELETING);
        assertThatThrownBy(() -> service.get(report.id(), 7)).isInstanceOf(ReportNotFoundException.class);

        storage.deleteFails = false;
        service.delete(report.id(), 7);
        assertThat(reports.rows).doesNotContainKey(report.id());
        assertThatThrownBy(() -> service.delete(report.id(), 7)).isInstanceOf(ReportNotFoundException.class);
    }

    @Test
    void aStorageOutageDuringDownloadDoesNotChangeTheReport() {
        var report = service.upload(command("uptime.csv", CSV.length, CSV));
        storage.openFails = true;

        assertThatThrownBy(() -> service.download(report.id(), 7)).isInstanceOf(ReportUnavailableException.class);

        assertThat(reports.row(report.id()).status()).isEqualTo(ReportStatus.READY);
    }

    @Test
    void anUnavailableAccessCheckIsNotSkipped() {
        var report = service.upload(command("uptime.csv", CSV.length, CSV));
        access.failure = new AccessUnavailableException("down", null);

        assertThatThrownBy(() -> service.get(report.id(), 7)).isInstanceOf(AccessUnavailableException.class);
        assertThatThrownBy(() -> service.download(report.id(), 7)).isInstanceOf(AccessUnavailableException.class);
    }

    @Test
    void validatesPagingBeforeAskingForAccess() {
        access.decision = AccessDecision.PROJECT_NOT_FOUND;

        assertThatThrownBy(() -> service.list(new ListReportsQuery(1, 7, null, null, 0, 0)))
                .hasMessageContaining("between 1 and 50");
        assertThatThrownBy(() -> service.list(new ListReportsQuery(1, 7, null, null, 0, 20)))
                .isInstanceOf(ReportNotFoundException.class);
    }

    private static UploadReportCommand command(String name, long size, byte[] content) {
        return new UploadReportCommand(42, 7, name, null, size, new ByteArrayInputStream(content));
    }
}
