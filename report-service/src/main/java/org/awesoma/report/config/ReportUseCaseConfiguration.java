package org.awesoma.report.config;

import java.time.Clock;
import org.awesoma.report.application.port.ObjectStorage;
import org.awesoma.report.application.port.ProjectAccessPort;
import org.awesoma.report.application.port.ReportRepository;
import org.awesoma.report.application.port.TransactionRunner;
import org.awesoma.report.application.usecase.ReportLimits;
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
    public ReportService reportService(ReportRepository reports, ObjectStorage storage, ProjectAccessPort access,
            TransactionRunner transactions, Clock clock, ReportProperties properties) {
        return new ReportService(reports, storage, access, transactions, clock, new ReportLimits(
                properties.maxFileSize().toBytes(), properties.uploadTimeout(), properties.maxPageSize()));
    }
}
