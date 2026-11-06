package org.awesoma.check.config;

import java.time.Clock;
import org.awesoma.check.application.port.CheckHistory;
import org.awesoma.check.application.port.CheckResultRecorder;
import org.awesoma.check.application.port.DeletionStore;
import org.awesoma.check.application.port.MonitorDirectory;
import org.awesoma.check.application.port.MonitorOwnership;
import org.awesoma.check.application.port.MonitorProbe;
import org.awesoma.check.application.port.ReactiveUnitOfWork;
import org.awesoma.check.application.port.ReportDataStore;
import org.awesoma.check.application.usecase.CheckHistoryService;
import org.awesoma.check.application.usecase.CheckRunner;
import org.awesoma.check.application.usecase.ReportDataService;
import org.awesoma.check.application.usecase.ResourceDeletionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CheckUseCases {
    @Bean CheckRunner checkRunner(MonitorDirectory directory, MonitorProbe probe, CheckResultRecorder recorder,
            @Value("${checker.batch-size}") int batchSize, @Value("${checker.concurrency}") int concurrency) {
        return new CheckRunner(directory, probe, recorder, batchSize, concurrency);
    }
    @Bean CheckHistoryService checkHistoryService(CheckHistory results, MonitorDirectory monitors) {
        return new CheckHistoryService(results, monitors);
    }
    @Bean ResourceDeletionService resourceDeletionService(DeletionStore store, ReactiveUnitOfWork work) {
        return new ResourceDeletionService(store, work);
    }
    @Bean ReportDataService reportDataService(ReportDataStore store, MonitorOwnership monitors) {
        return new ReportDataService(store, monitors, Clock.systemUTC());
    }
}
