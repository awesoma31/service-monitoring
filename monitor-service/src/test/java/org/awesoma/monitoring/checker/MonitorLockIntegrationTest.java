package org.awesoma.monitoring.checker;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.awesoma.monitoring.domain.entity.Monitor;
import org.awesoma.monitoring.domain.entity.Project;
import org.awesoma.monitoring.domain.entity.User;
import org.awesoma.monitoring.domain.enums.IncidentStatus;
import org.awesoma.monitoring.domain.enums.MonitorState;
import org.awesoma.monitoring.domain.model.ProbeOutcome;
import org.awesoma.monitoring.repository.IncidentRepository;
import org.awesoma.monitoring.repository.MonitorRepository;
import org.awesoma.monitoring.service.MonitorCheckService;
import org.awesoma.monitoring.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionTemplate;
import org.awesoma.monitoring.integration.IncidentChanged;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The row lock taken by {@link MonitorCheckService#record} against a real database.
 *
 * <p>Unlike the other integration tests these run outside a test transaction: a lock only
 * shows when separate transactions compete for the same row, so every step commits and the
 * data is removed afterwards.
 */
class MonitorLockIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MonitorCheckService checks;
    @Autowired private MonitorRepository monitors;
    @Autowired private IncidentRepository incidents;
    @Autowired private TransactionTemplate transactions;
    @Autowired private EntityManager entityManager;

    private final ExecutorService workers = Executors.newFixedThreadPool(8);
    private Long monitorId;
    private Long ownerId;

    @BeforeEach
    void seed() {
        String slug = "lock-" + System.nanoTime();
        transactions.executeWithoutResult(status -> {
            User owner = new User();
            owner.setEmail(slug + "@example.com");
            owner.setPassword("secret123");
            owner.setFullName("Owner");
            entityManager.persist(owner);

            Project project = new Project();
            project.setOwner(owner);
            project.setName("Lock");
            project.setSlug(slug);
            project.addMember(owner);
            entityManager.persist(project);

            Monitor monitor = new Monitor();
            monitor.setProject(project);
            monitor.setName("Monitor");
            monitor.setUrl("https://example.com");
            monitor.setIntervalSec(60);
            monitor.setTimeoutMs(5000);
            entityManager.persist(monitor);

            ownerId = owner.getId();
            monitorId = monitor.getId();
        });
    }

    @AfterEach
    void cleanUp() throws InterruptedException {
        workers.shutdownNow();
        workers.awaitTermination(10, TimeUnit.SECONDS);
        // Deleting the owner's project cascades to the monitor and everything recorded for it.
        transactions.executeWithoutResult(status -> {
            entityManager.createQuery("delete from Project p where p.owner.id = :owner")
                    .setParameter("owner", ownerId)
                    .executeUpdate();
            entityManager.createQuery("delete from User u where u.id = :id")
                    .setParameter("id", ownerId)
                    .executeUpdate();
        });
    }

    @Test
    void recordingWaitsWhileAnotherTransactionHoldsTheMonitorRow() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> holder = workers.submit(() -> transactions.executeWithoutResult(status -> {
            monitors.findWithLockById(monitorId).orElseThrow();
            locked.countDown();
            await(release);
        }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

        Future<?> recorder = workers.submit(
                () -> checks.record(monitorId, ProbeOutcome.connectionError(5, "refused")));

        assertThat(finishesWithin(recorder, 1_000))
                .as("record must block on the row lock held by the other transaction")
                .isFalse();

        release.countDown();
        holder.get(10, TimeUnit.SECONDS);
        recorder.get(10, TimeUnit.SECONDS);

        assertThat(monitors.findById(monitorId).orElseThrow().getLastCheckedAt())
                .as("the outcome is applied once the lock is released")
                .isNotNull();
    }

    @Test
    void concurrentFailuresOfOneMonitorOpenExactlyOneIncident() throws Exception {
        int workersCount = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        for (int i = 0; i < workersCount; i++) {
            results.add(workers.submit(() -> {
                await(start);
                checks.record(monitorId, ProbeOutcome.connectionError(5, "refused"));
            }));
        }
        start.countDown();
        for (Future<?> result : results) {
            // Without the lock the losers of the race fail on the one-open-incident index.
            result.get(30, TimeUnit.SECONDS);
        }

        assertThat(incidents.findByMonitorId(monitorId, Pageable.unpaged()))
                .singleElement()
                .satisfies(incident -> assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN));
        assertThat(monitors.findById(monitorId).orElseThrow().getCurrentState())
                .isEqualTo(MonitorState.DOWN);
        verify(notificationClient, times(1)).incidentChanged(
                argThat(event -> event.kind() == IncidentChanged.Kind.OPENED));
    }

    private static boolean finishesWithin(Future<?> future, long millis) throws Exception {
        try {
            future.get(millis, TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException stillRunning) {
            return false;
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for the other worker");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
