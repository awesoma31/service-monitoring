package org.awesoma.check.scheduling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.awesoma.check.service.CheckRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Starts a pass at a fixed delay after the previous one ends, so passes never overlap. The
 * pass itself is reactive; waiting for it here blocks only the scheduler's own thread.
 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "checker.enabled", havingValue = "true", matchIfMissing = true)
public class CheckScheduler {

    private final CheckRunner runner;

    @Scheduled(fixedDelayString = "${checker.interval-ms}")
    public void runDueChecks() {
        try {
            runner.runOnce().block();
        } catch (RuntimeException failure) {
            log.warn("Check pass failed, retrying on the next tick", failure);
        }
    }
}
