package org.awesoma.report.config;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.awesoma.report.application.port.EventInbox;
import org.awesoma.report.application.port.IdempotencyKeys;
import org.awesoma.report.application.port.ObjectStorage;
import org.awesoma.report.application.port.ProjectAccessPort;
import org.awesoma.report.application.port.ProjectGuard;
import org.awesoma.report.application.port.ReportDataSource;
import org.awesoma.report.application.port.ReportJobQueue;
import org.awesoma.report.application.port.ReportRenderer;
import org.awesoma.report.application.port.ReportRepository;
import org.awesoma.report.application.port.TransactionRunner;
import org.awesoma.report.application.usecase.GenerationLimits;
import org.awesoma.report.application.usecase.GenerationService;
import org.awesoma.report.application.usecase.GenerationWorker;
import org.awesoma.report.application.usecase.ProjectDeletionService;
import org.awesoma.report.application.usecase.ReportCleanup;
import org.awesoma.report.application.usecase.ReportLimits;
import org.awesoma.report.application.usecase.ReportMaintenance;
import org.awesoma.report.application.usecase.ReportService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The use cases are plain classes; this is where they meet the adapters. */
@Configuration(proxyBeanMethods = false)
public class ReportUseCaseConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public GenerationLimits generationLimits(ReportProperties properties) {
        var generation = properties.generation();
        return new GenerationLimits(Duration.ofDays(generation.maxPeriodDays()), generation.maxMonitors(),
                generation.maxSourceRows(), generation.pageSize(), generation.timeout(), generation.maxAttempts(),
                generation.retryDelay(), generation.leaseDuration());
    }

    @Bean
    public ReportCleanup reportCleanup(ReportRepository reports, ObjectStorage storage, IdempotencyKeys keys,
            TransactionRunner transactions, Clock clock, ReportProperties properties) {
        return new ReportCleanup(reports, storage, keys, transactions, clock, properties.idempotencyTombstoneTtl());
    }

    @Bean
    public ReportService reportService(ReportRepository reports, ObjectStorage storage, ProjectAccessPort access,
            ProjectGuard projects, ReportCleanup cleanup, TransactionRunner transactions, Clock clock,
            ReportProperties properties) {
        return new ReportService(reports, storage, access, projects, cleanup, transactions, clock, new ReportLimits(
                properties.maxFileSize().toBytes(), properties.uploadTimeout(), properties.maxPageSize()));
    }

    @Bean
    public GenerationService generationService(ReportRepository reports, IdempotencyKeys keys,
            ReportDataSource sources, ProjectAccessPort access, ProjectGuard projects, TransactionRunner transactions,
            Clock clock, GenerationLimits limits) {
        return new GenerationService(reports, keys, sources, access, projects, transactions, clock, limits);
    }

    @Bean
    public GenerationWorker generationWorker(ReportRepository reports, ReportJobQueue queue, ReportDataSource sources,
            ProjectAccessPort access, ProjectGuard projects, ObjectStorage storage, List<ReportRenderer> renderers,
            ReportCleanup cleanup, TransactionRunner transactions, Clock clock, GenerationLimits limits,
            ReportProperties properties) {
        return new GenerationWorker(reports, queue, sources, access, projects, storage, renderers, cleanup,
                transactions, clock, limits, properties.maxFileSize().toBytes());
    }

    @Bean
    public ReportMaintenance reportMaintenance(ReportRepository reports, ObjectStorage storage, IdempotencyKeys keys,
            ReportCleanup cleanup, Clock clock, ReportProperties properties) {
        return new ReportMaintenance(reports, storage, keys, cleanup, clock, properties.reconciliationGrace());
    }

    @Bean
    public ProjectDeletionService projectDeletionService(ReportRepository reports, ProjectGuard projects,
            EventInbox inbox, TransactionRunner transactions, Clock clock) {
        return new ProjectDeletionService(reports, projects, inbox, transactions, clock);
    }
}
