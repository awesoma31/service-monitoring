package org.awesoma.report.adapters.in.scheduling;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.awesoma.report.application.usecase.GenerationWorker;
import org.awesoma.report.application.usecase.ReportMaintenance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the background work of this instance: at most {@code concurrency} generation jobs at
 * once, recovery of jobs whose worker died, cleanup of deleted reports, expiry of
 * Idempotency-Key tombstones and reconciliation of the bucket with the metadata. Every
 * instance runs it; the database decides which instance takes which job.
 */
@Slf4j
@Component
@EnableScheduling
@ConditionalOnProperty(name = "report.jobs.enabled", havingValue = "true", matchIfMissing = true)
class ReportJobsScheduler {
    private final GenerationWorker worker;
    private final ReportMaintenance maintenance;
    private final Semaphore slots;
    private final ExecutorService executor;

    ReportJobsScheduler(GenerationWorker worker, ReportMaintenance maintenance,
            @Value("${report.generation.concurrency:2}") int concurrency) {
        this.worker = worker;
        this.maintenance = maintenance;
        this.slots = new Semaphore(concurrency);
        this.executor = Executors.newFixedThreadPool(concurrency);
    }

    @Scheduled(fixedDelayString = "${report.jobs.poll-interval:PT2S}")
    void generate() {
        while (slots.tryAcquire()) {
            executor.execute(() -> {
                try {
                    while (worker.runNext()) {
                        // Keep taking jobs while there are due ones.
                    }
                } catch (RuntimeException error) {
                    log.error("Generation worker stopped unexpectedly", error);
                } finally {
                    slots.release();
                }
            });
        }
    }

    @Scheduled(fixedDelayString = "${report.jobs.recovery-interval:PT30S}")
    void recover() {
        guarded("recovery", () -> worker.recoverExpired(20));
    }

    @Scheduled(fixedDelayString = "${report.jobs.cleanup-interval:PT30S}")
    void cleanup() {
        guarded("cleanup", () -> {
            maintenance.purgeDeleted(50);
            maintenance.removeExpiredKeys();
        });
    }

    @Scheduled(fixedDelayString = "${report.jobs.reconciliation-interval:PT10M}",
            initialDelayString = "${report.jobs.reconciliation-interval:PT10M}")
    void reconcile() {
        guarded("reconciliation", maintenance::reconcile);
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
    }

    private static void guarded(String task, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException error) {
            log.warn("Background task failed and will run again: task={}", task, error);
        }
    }
}
