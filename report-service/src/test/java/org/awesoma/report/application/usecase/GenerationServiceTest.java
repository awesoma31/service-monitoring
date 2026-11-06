package org.awesoma.report.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.LongStream;
import org.awesoma.report.application.port.AccessDecision;
import org.awesoma.report.application.port.ProjectNotFoundException;
import org.awesoma.report.application.port.SourceRejectedException;
import org.awesoma.report.application.port.SourceUnavailableException;
import org.awesoma.report.domain.FailureCode;
import org.awesoma.report.domain.InvalidReportRequestException;
import org.awesoma.report.domain.ReportFormat;
import org.awesoma.report.domain.ReportStatus;
import org.junit.jupiter.api.Test;

class GenerationServiceTest {
    static final GenerationLimits LIMITS = new GenerationLimits(Duration.ofDays(31), 100, 100_000, 50,
            Duration.ofMinutes(5), 3, Duration.ofSeconds(30), Duration.ofMinutes(1));
    private static final OffsetDateTime FROM = OffsetDateTime.parse("2026-10-01T00:00:00Z");
    private static final OffsetDateTime TO = OffsetDateTime.parse("2026-10-02T03:00:00+03:00");

    private final Fakes.Reports reports = new Fakes.Reports();
    private final Fakes.Keys keys = new Fakes.Keys();
    private final Fakes.Sources sources = new Fakes.Sources();
    private final Fakes.Access access = new Fakes.Access();
    private final Fakes.Projects projects = new Fakes.Projects();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-02T12:00:00Z"));
    private final GenerationService service = new GenerationService(reports, keys, sources, access, projects,
            Fakes.TRANSACTIONS, clock, LIMITS);

    @Test
    void queuesAJobWithTheNormalisedPeriod() {
        var report = service.generate(command(List.of(12L, 11L), "key-1"));

        assertThat(report.status()).isEqualTo(ReportStatus.QUEUED);
        assertThat(report.generation().period().to()).isEqualTo(Instant.parse("2026-10-02T00:00:00Z"));
        assertThat(report.generation().selection().monitorIds()).containsExactly(11L, 12L);
        assertThat(report.generation().requestedAt()).isEqualTo(clock.instant());
        assertThat(report.createdBy()).isEqualTo(7);
        assertThat(reports.rows).containsKey(report.id());
        assertThat(projects.locked).containsExactly(42L);
    }

    @Test
    void aRepeatedKeyReturnsTheSameJob() {
        var first = service.generate(command(List.of(11L, 12L), "key-1"));
        clock.advance(Duration.ofSeconds(5));

        var again = service.generate(command(List.of(12L, 11L), "key-1"));

        assertThat(again.id()).isEqualTo(first.id());
        assertThat(reports.rows).hasSize(1);
        assertThat(service.generate(command(List.of(11L), null)).id()).isNotEqualTo(first.id());
        assertThat(service.generate(command(List.of(11L), "key-2")).id()).isNotEqualTo(first.id());
    }

    @Test
    void aRetryGetsItsJobEvenWhileTheMonitorsCannotBeChecked() {
        var first = service.generate(command(List.of(11L), "key-1"));
        sources.failOnCall.put("monitors#2", () -> new SourceUnavailableException("down", null));

        assertThat(service.generate(command(List.of(11L), "key-1")).id()).isEqualTo(first.id());
        assertThat(sources.calls.get("monitors")).isEqualTo(1);
    }

    @Test
    void aKeyReusedWithOtherParametersConflicts() {
        service.generate(command(List.of(11L), "key-1"));

        assertThatThrownBy(() -> service.generate(command(List.of(12L), "key-1")))
                .isInstanceOf(IdempotencyConflictException.class)
                .extracting("code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThatThrownBy(() -> service.generate(new GenerateReportCommand(42, 7, FROM, TO,
                ReportFormat.PDF, List.of(11L), "key-1"))).isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void aKeyOfADeletedReportStaysTakenUntilItsTombstoneExpires() {
        var first = service.generate(command(List.of(11L), "key-1"));
        reports.delete(first.id());
        keys.tombstone(first.id(), clock.instant().plus(Duration.ofHours(24)));

        assertThatThrownBy(() -> service.generate(command(List.of(11L), "key-1")))
                .isInstanceOf(IdempotencyConflictException.class)
                .extracting("code").isEqualTo("REPORT_DELETED");

        clock.advance(Duration.ofHours(24));
        var fresh = service.generate(command(List.of(11L), "key-1"));
        assertThat(fresh.id()).isNotEqualTo(first.id());
    }

    @Test
    void aKeyOfAReportBeingDeletedConflicts() {
        var first = service.generate(command(List.of(11L), "key-1"));
        reports.row(first.id()).markDeleting(clock.instant());

        assertThatThrownBy(() -> service.generate(command(List.of(11L), "key-1")))
                .isInstanceOf(IdempotencyConflictException.class)
                .extracting("code").isEqualTo("REPORT_DELETED");
    }

    @Test
    void refusesInvalidRequestsBeforeStoringAnything() {
        assertThatThrownBy(() -> service.generate(new GenerateReportCommand(42, 7, FROM, TO, null, null, null)))
                .isInstanceOf(InvalidReportRequestException.class).extracting("field").isEqualTo("format");
        assertThatThrownBy(() -> service.generate(command(List.of(), null)))
                .isInstanceOf(InvalidReportRequestException.class).extracting("field").isEqualTo("monitor_ids");
        assertThatThrownBy(() -> service.generate(new GenerateReportCommand(42, 7, FROM, FROM.plusDays(32),
                ReportFormat.CSV, null, null))).isInstanceOf(InvalidReportRequestException.class);
        assertThatThrownBy(() -> service.generate(new GenerateReportCommand(42, 7, FROM,
                OffsetDateTime.parse("2026-11-03T00:00:00Z"), ReportFormat.CSV, null, null)))
                .isInstanceOf(InvalidReportRequestException.class);
        var tooMany = LongStream.rangeClosed(1, 101).boxed().toList();
        assertThatThrownBy(() -> service.generate(command(tooMany, null)))
                .isInstanceOf(InvalidReportRequestException.class).hasMessageContaining("100");
        assertThatThrownBy(() -> service.generate(command(null, "x".repeat(256))))
                .isInstanceOf(InvalidReportRequestException.class).extracting("field").isEqualTo("Idempotency-Key");

        assertThat(reports.rows).isEmpty();
        assertThat(keys.records).isEmpty();
    }

    @Test
    void checksTheMonitorsOfTheProjectWhenTheJobIsRequested() {
        sources.monitors.clear();
        assertThatThrownBy(() -> service.generate(command(null, null)))
                .isInstanceOf(InvalidReportRequestException.class).hasMessageContaining("no monitors");

        sources.failOnCall.put("monitors#2", () -> new SourceRejectedException(FailureCode.MONITOR_NOT_IN_PROJECT, "x"));
        assertThatThrownBy(() -> service.generate(command(List.of(99L), null)))
                .isInstanceOf(InvalidReportRequestException.class).extracting("field").isEqualTo("monitor_ids");
        sources.failOnCall.put("monitors#3", () -> new ProjectNotFoundException(42));
        assertThatThrownBy(() -> service.generate(command(List.of(11L), null)))
                .isInstanceOf(ReportNotFoundException.class);
        sources.failOnCall.put("monitors#4", () -> new SourceUnavailableException("down", null));
        assertThatThrownBy(() -> service.generate(command(List.of(11L), null)))
                .isInstanceOf(ReportUnavailableException.class);
        assertThat(reports.rows).isEmpty();
    }

    @Test
    void needsTheRightToWriteAndAProjectThatWasNotDeleted() {
        access.decision = AccessDecision.DENIED;
        assertThatThrownBy(() -> service.generate(command(null, null))).isInstanceOf(ProjectAccessDeniedException.class);

        access.decision = AccessDecision.ALLOWED;
        projects.markDeleted(42);
        assertThatThrownBy(() -> service.generate(command(null, null))).isInstanceOf(ReportNotFoundException.class);
        assertThat(reports.rows).isEmpty();
    }

    private static GenerateReportCommand command(List<Long> monitorIds, String key) {
        return new GenerateReportCommand(42, 7, FROM, TO, ReportFormat.CSV, monitorIds, key);
    }
}
