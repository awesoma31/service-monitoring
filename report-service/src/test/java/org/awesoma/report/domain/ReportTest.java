package org.awesoma.report.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ReportTest {
    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");
    private static final Duration LEASE = Duration.ofMinutes(1);
    private static final Duration RETRY_DELAY = Duration.ofSeconds(30);
    private static final int MAX_ATTEMPTS = 3;
    private static final String HASH = "a".repeat(64);
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private static Lease lease(Instant now) {
        return new Lease(UUID.randomUUID(), UUID.randomUUID(), now.plus(LEASE));
    }

    private static Report upload(String name, Lease lease) {
        return Report.startUpload(ID, 42, 7, FileName.fromClient(name), lease, NOW);
    }

    private static Report queued() {
        var period = new ReportPeriod(NOW.minus(Duration.ofDays(1)), NOW);
        return Report.queueGeneration(ID, 42, 7, ReportFormat.PDF,
                new GenerationParameters(period, MonitorSelection.allMonitors(), NOW));
    }

    @Nested
    class Upload {

        @Test
        void startsUploadingUnderItsOwnKey() {
            var lease = lease(NOW);
            var report = upload("uptime.CSV", lease);

            assertThat(report.status()).isEqualTo(ReportStatus.UPLOADING);
            assertThat(report.source()).isEqualTo(ReportSource.UPLOADED);
            assertThat(report.format()).isEqualTo(ReportFormat.CSV);
            assertThat(report.objectKey()).isEqualTo(ObjectKeys.upload(42, ID)).doesNotContain("uptime");
            assertThat(report.sizeBytes()).isNull();
            assertThat(report.sha256()).isNull();
            assertThat(report.createdBy()).isEqualTo(7);
            assertThat(report.visible()).isTrue();
            assertThat(report.contentAvailable()).isFalse();
        }

        @Test
        void acceptsOnlyCsvAndPdfFiles() {
            assertThatThrownBy(() -> upload("notes.txt", lease(NOW)))
                    .isInstanceOf(InvalidReportRequestException.class);
            assertThatThrownBy(() -> upload("README", lease(NOW)))
                    .isInstanceOf(InvalidReportRequestException.class);
        }

        @Test
        void becomesReadyWithSizeAndHash() {
            var lease = lease(NOW);
            var report = upload("uptime.pdf", lease);

            report.completeUpload(lease.token(), 1024, HASH, NOW.plusSeconds(5));

            assertThat(report.status()).isEqualTo(ReportStatus.READY);
            assertThat(report.sizeBytes()).isEqualTo(1024);
            assertThat(report.sha256()).isEqualTo(HASH);
            assertThat(report.lease()).isNull();
            assertThat(report.updatedAt()).isEqualTo(NOW.plusSeconds(5));
            assertThat(report.contentAvailable()).isTrue();
        }

        @Test
        void requiresTheSizeAndHashOfTheStoredBytes() {
            var lease = lease(NOW);
            var report = upload("uptime.pdf", lease);

            assertThatThrownBy(() -> report.completeUpload(lease.token(), 0, HASH, NOW))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> report.completeUpload(lease.token(), 10, "etag", NOW))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(report.status()).isEqualTo(ReportStatus.UPLOADING);
        }

        @Test
        void aStrangerOrALateUploaderCannotFinishIt() {
            var lease = lease(NOW);
            var report = upload("uptime.pdf", lease);

            assertThatThrownBy(() -> report.completeUpload(UUID.randomUUID(), 1, HASH, NOW))
                    .isInstanceOf(StaleAttemptException.class);
            assertThatThrownBy(() -> report.completeUpload(lease.token(), 1, HASH, lease.until()))
                    .isInstanceOf(StaleAttemptException.class);
            assertThat(report.status()).isEqualTo(ReportStatus.UPLOADING);
        }

        @Test
        void aFailedUploadKeepsItsKeyForCleanup() {
            var lease = lease(NOW);
            var report = upload("uptime.pdf", lease);

            report.failUpload(lease.token(), NOW.plus(LEASE).plusSeconds(1));

            assertThat(report.status()).isEqualTo(ReportStatus.FAILED);
            assertThat(report.failure()).isEqualTo(FailureCode.UPLOAD_FAILED);
            assertThat(report.objectKey()).isEqualTo(ObjectKeys.upload(42, ID));
            assertThat(report.visible()).isTrue();
        }

        @Test
        void generationStepsDoNotApplyToUploads() {
            var lease = lease(NOW);
            var report = upload("uptime.pdf", lease);

            assertThat(report.dueAt(NOW)).isFalse();
            assertThatThrownBy(() -> report.startAttempt(lease(NOW), NOW)).isInstanceOf(ReportStateException.class);
            assertThatThrownBy(() -> report.completeGeneration(lease.token(), 1, HASH, NOW))
                    .isInstanceOf(ReportStateException.class);
        }
    }

    @Nested
    class Generation {

        @Test
        void isQueuedWithAServerChosenName() {
            var report = queued();

            assertThat(report.status()).isEqualTo(ReportStatus.QUEUED);
            assertThat(report.source()).isEqualTo(ReportSource.GENERATED);
            assertThat(report.fileName().value()).isEqualTo("report-" + ID + ".pdf");
            assertThat(report.objectKey()).isNull();
            assertThat(report.attempts()).isZero();
            assertThat(report.createdAt()).isEqualTo(NOW);
            assertThat(report.generation().requestedAt()).isEqualTo(NOW);
            assertThat(report.dueAt(NOW)).isTrue();
        }

        @Test
        void eachAttemptGetsANewKeyAndLease() {
            var report = queued();
            var first = lease(NOW);

            report.startAttempt(first, NOW);

            assertThat(report.status()).isEqualTo(ReportStatus.GENERATING);
            assertThat(report.attempts()).isEqualTo(1);
            assertThat(report.lease()).isEqualTo(first);
            assertThat(report.objectKey()).isEqualTo(ObjectKeys.attempt(42, ID, first.attemptId()));

            report.retryLater(first.token(), FailureCode.SOURCE_UNAVAILABLE, RETRY_DELAY, MAX_ATTEMPTS, NOW);
            var second = lease(NOW.plusSeconds(30));
            report.startAttempt(second, NOW.plusSeconds(30));

            assertThat(report.objectKey()).isEqualTo(ObjectKeys.attempt(42, ID, second.attemptId()))
                    .isNotEqualTo(ObjectKeys.attempt(42, ID, first.attemptId()));
            assertThat(report.attempts()).isEqualTo(2);
        }

        @Test
        void publishesOnlyTheCurrentAttemptWhileItHoldsTheLease() {
            var report = queued();
            var lease = lease(NOW);
            report.startAttempt(lease, NOW);
            report.recordReadBounds(lease.token(), new ReadBounds(NOW, 900L, 40L), NOW.plusSeconds(1));

            report.completeGeneration(lease.token(), 2048, HASH, NOW.plusSeconds(10));

            assertThat(report.status()).isEqualTo(ReportStatus.READY);
            assertThat(report.generatedAt()).isEqualTo(NOW.plusSeconds(10));
            assertThat(report.readBounds().checksUpperId()).isEqualTo(900L);
            assertThat(report.lease()).isNull();
        }

        @Test
        void heartbeatExtendsTheLease() {
            var report = queued();
            var lease = lease(NOW);
            report.startAttempt(lease, NOW);

            report.renewLease(lease.token(), NOW.plus(LEASE).plusSeconds(50), NOW.plusSeconds(50));
            report.completeGeneration(lease.token(), 1, HASH, NOW.plus(LEASE).plusSeconds(10));

            assertThat(report.status()).isEqualTo(ReportStatus.READY);
        }

        @Test
        void anAttemptThatLostItsLeaseCannotPublish() {
            var report = queued();
            var lease = lease(NOW);
            report.startAttempt(lease, NOW);

            assertThatThrownBy(() -> report.completeGeneration(lease.token(), 1, HASH, lease.until()))
                    .isInstanceOf(StaleAttemptException.class);
            assertThatThrownBy(() -> report.renewLease(lease.token(), lease.until().plus(LEASE), lease.until()))
                    .isInstanceOf(StaleAttemptException.class);
            assertThat(report.status()).isEqualTo(ReportStatus.GENERATING);
        }

        @Test
        void anOlderAttemptCannotPublishOverANewerOne() {
            var report = queued();
            var old = lease(NOW);
            report.startAttempt(old, NOW);
            report.expireLease(FailureCode.TIMED_OUT, RETRY_DELAY, MAX_ATTEMPTS, old.until());
            var current = lease(NOW.plus(LEASE).plus(RETRY_DELAY));
            report.startAttempt(current, NOW.plus(LEASE).plus(RETRY_DELAY));

            assertThatThrownBy(() -> report.completeGeneration(old.token(), 1, HASH, NOW.plusSeconds(90)))
                    .isInstanceOf(StaleAttemptException.class);
            assertThatThrownBy(() -> report.fail(old.token(), FailureCode.LIMIT_EXCEEDED, NOW.plusSeconds(90)))
                    .isInstanceOf(StaleAttemptException.class);
            assertThat(report.lease()).isEqualTo(current);
        }

        @Test
        void retriesWithAGrowingDelayAndThenFails() {
            var report = queued();
            Instant now = NOW;
            for (int attempt = 1; attempt < MAX_ATTEMPTS; attempt++) {
                var lease = lease(now);
                report.startAttempt(lease, now);
                report.retryLater(lease.token(), FailureCode.SOURCE_UNAVAILABLE, RETRY_DELAY, MAX_ATTEMPTS, now);

                Duration expected = RETRY_DELAY.multipliedBy(1L << (attempt - 1));
                assertThat(report.status()).isEqualTo(ReportStatus.QUEUED);
                assertThat(report.nextAttemptAt()).isEqualTo(now.plus(expected));
                assertThat(report.dueAt(report.nextAttemptAt().minusMillis(1))).isFalse();
                assertThat(report.failure()).isNull();
                now = report.nextAttemptAt();
            }
            var last = lease(now);
            report.startAttempt(last, now);
            report.retryLater(last.token(), FailureCode.SOURCE_UNAVAILABLE, RETRY_DELAY, MAX_ATTEMPTS, now);

            assertThat(report.status()).isEqualTo(ReportStatus.FAILED);
            assertThat(report.failure()).isEqualTo(FailureCode.SOURCE_UNAVAILABLE);
            assertThat(report.attempts()).isEqualTo(MAX_ATTEMPTS);
            assertThat(report.lease()).isNull();
        }

        @Test
        void cannotStartBeforeTheRetryIsDue() {
            var report = queued();
            var lease = lease(NOW);
            report.startAttempt(lease, NOW);
            report.retryLater(lease.token(), FailureCode.STORAGE_UNAVAILABLE, RETRY_DELAY, MAX_ATTEMPTS, NOW);

            assertThatThrownBy(() -> report.startAttempt(lease(NOW), NOW.plusSeconds(29)))
                    .isInstanceOf(ReportStateException.class);
        }

        @Test
        void permanentFailuresAreNotRetried() {
            var report = queued();
            var lease = lease(NOW);
            report.startAttempt(lease, NOW);

            report.fail(lease.token(), FailureCode.ACCESS_REVOKED, NOW.plusSeconds(1));

            assertThat(report.status()).isEqualTo(ReportStatus.FAILED);
            assertThat(report.failure()).isEqualTo(FailureCode.ACCESS_REVOKED);
            assertThat(report.dueAt(NOW.plus(Duration.ofDays(1)))).isFalse();
        }

        @Test
        void onlyAnExpiredLeaseCanBeRecovered() {
            var report = queued();
            var lease = lease(NOW);

            assertThatThrownBy(() -> report.expireLease(FailureCode.TIMED_OUT, RETRY_DELAY, MAX_ATTEMPTS, NOW))
                    .isInstanceOf(ReportStateException.class);
            report.startAttempt(lease, NOW);
            assertThatThrownBy(() -> report.expireLease(FailureCode.TIMED_OUT, RETRY_DELAY, MAX_ATTEMPTS,
                    lease.until().minusMillis(1))).isInstanceOf(ReportStateException.class);

            report.expireLease(FailureCode.TIMED_OUT, RETRY_DELAY, MAX_ATTEMPTS, lease.until());

            assertThat(report.status()).isEqualTo(ReportStatus.QUEUED);
            assertThat(report.lease()).isNull();
        }
    }

    @Nested
    class Deletion {

        @Test
        void hidesTheReportAndRevokesTheLease() {
            var report = queued();
            var lease = lease(NOW);
            report.startAttempt(lease, NOW);

            assertThat(report.markDeleting(NOW.plusSeconds(1))).isTrue();

            assertThat(report.status()).isEqualTo(ReportStatus.DELETING);
            assertThat(report.visible()).isFalse();
            assertThat(report.lease()).isNull();
            assertThatThrownBy(() -> report.completeGeneration(lease.token(), 1, HASH, NOW.plusSeconds(2)))
                    .isInstanceOf(StaleAttemptException.class);
            assertThatThrownBy(() -> report.retryLater(lease.token(), FailureCode.SOURCE_UNAVAILABLE, RETRY_DELAY,
                    MAX_ATTEMPTS, NOW.plusSeconds(2))).isInstanceOf(StaleAttemptException.class);
        }

        @Test
        void aRepeatedDeleteChangesNothing() {
            var report = queued();
            report.markDeleting(NOW.plusSeconds(1));

            assertThat(report.markDeleting(NOW.plusSeconds(2))).isFalse();
            assertThat(report.updatedAt()).isEqualTo(NOW.plusSeconds(1));
        }

        @Test
        void aLateUploaderCannotReviveADeletedUpload() {
            var lease = lease(NOW);
            var report = upload("uptime.csv", lease);
            report.markDeleting(NOW.plusSeconds(1));

            assertThatThrownBy(() -> report.completeUpload(lease.token(), 1, HASH, NOW.plusSeconds(2)))
                    .isInstanceOf(StaleAttemptException.class);
            assertThat(report.status()).isEqualTo(ReportStatus.DELETING);
        }
    }

    @Nested
    class MissingObject {

        @Test
        void aReadyReportWithoutItsObjectFails() {
            var lease = lease(NOW);
            var report = upload("uptime.csv", lease);
            report.completeUpload(lease.token(), 1, HASH, NOW);

            report.markObjectMissing(NOW.plusSeconds(1));

            assertThat(report.status()).isEqualTo(ReportStatus.FAILED);
            assertThat(report.failure()).isEqualTo(FailureCode.OBJECT_MISSING);
            assertThat(report.contentAvailable()).isFalse();
        }

        @Test
        void doesNotUndoADeletion() {
            var report = queued();
            report.markDeleting(NOW);

            assertThatThrownBy(() -> report.markObjectMissing(NOW)).isInstanceOf(ReportStateException.class);
            assertThat(report.status()).isEqualTo(ReportStatus.DELETING);
        }
    }

    @Nested
    class Persistence {

        @Test
        void restoresExactlyWhatWasSaved() {
            var report = queued();
            var lease = lease(NOW);
            report.startAttempt(lease, NOW);
            report.recordReadBounds(lease.token(), new ReadBounds(NOW, 5L, null), NOW);

            var restored = Report.restore(report.snapshot());

            assertThat(restored.snapshot()).isEqualTo(report.snapshot());
            assertThat(restored.id()).isEqualTo(ID);
            assertThat(restored.projectId()).isEqualTo(42);
        }

        @Test
        void generationParametersBelongOnlyToGeneratedReports() {
            var uploaded = upload("uptime.csv", lease(NOW)).snapshot();
            var generated = queued().snapshot();
            var mixed = new ReportSnapshot(uploaded.id(), uploaded.projectId(), uploaded.createdBy(),
                    uploaded.source(), uploaded.format(), uploaded.fileName(), uploaded.status(),
                    uploaded.createdAt(), uploaded.updatedAt(), uploaded.objectKey(), null, null, null,
                    generated.generation(), uploaded.lease(), 1, null, null, null);

            assertThatThrownBy(() -> Report.restore(mixed)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
