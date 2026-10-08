package org.awesoma.monitoring.checker;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.awesoma.monitoring.domain.entity.Monitor;
import org.awesoma.monitoring.domain.entity.Project;
import org.awesoma.monitoring.domain.entity.User;
import org.awesoma.monitoring.domain.enums.MonitorState;
import org.awesoma.monitoring.domain.model.MonitorTarget;
import org.awesoma.monitoring.domain.model.ProbeOutcome;
import org.awesoma.monitoring.repository.MonitorRepository;
import org.awesoma.monitoring.service.MonitorCheckService;
import org.awesoma.monitoring.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/** Check leases against real PostgreSQL locking and independent transactions. */
class MonitorClaimIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MonitorCheckService checks;
    @Autowired private MonitorRepository monitors;
    @Autowired private TransactionTemplate transactions;
    @Autowired private EntityManager entityManager;

    private final ExecutorService workers = Executors.newFixedThreadPool(2);
    private Long ownerId;

    @BeforeEach
    void seed() {
        String slug = "claims-" + System.nanoTime();
        transactions.executeWithoutResult(status -> {
            User owner = new User();
            owner.setEmail(slug + "@example.com");
            owner.setPassword("secret123");
            owner.setFullName("Owner");
            entityManager.persist(owner);

            Project project = new Project();
            project.setOwner(owner);
            project.setName("Claims");
            project.setSlug(slug);
            project.addMember(owner);
            entityManager.persist(project);

            for (int index = 0; index < 4; index++) {
                Monitor monitor = new Monitor();
                monitor.setProject(project);
                monitor.setName("Monitor " + index);
                monitor.setUrl("https://example.com/" + index);
                monitor.setIntervalSec(60);
                monitor.setTimeoutMs(5000);
                entityManager.persist(monitor);
            }
            ownerId = owner.getId();
        });
    }

    @AfterEach
    void cleanUp() throws InterruptedException {
        workers.shutdownNow();
        workers.awaitTermination(10, TimeUnit.SECONDS);
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
    void concurrentWorkersClaimDisjointBatches() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        Future<List<MonitorTarget>> first = workers.submit(() -> claimAfter(start, 2));
        Future<List<MonitorTarget>> second = workers.submit(() -> claimAfter(start, 2));

        start.countDown();
        List<MonitorTarget> firstBatch = first.get(20, TimeUnit.SECONDS);
        List<MonitorTarget> secondBatch = second.get(20, TimeUnit.SECONDS);

        Set<Long> firstIds = ids(firstBatch);
        Set<Long> secondIds = ids(secondBatch);
        assertThat(firstIds).doesNotContainAnyElementsOf(secondIds);
        assertThat(firstIds).hasSize(2);
        assertThat(secondIds).hasSize(2);
        Set<Long> allIds = new HashSet<>(firstIds);
        allIds.addAll(secondIds);
        assertThat(allIds).hasSize(4);
        assertThat(firstBatch).extracting(MonitorTarget::claimToken).doesNotContainNull();
        assertThat(secondBatch).extracting(MonitorTarget::claimToken).doesNotContainNull();
    }

    @Test
    void anExpiredLeaseCanBeReclaimedAndItsOldOutcomeCannotWin() {
        MonitorTarget oldClaim = checks.claimDueTargets(4, 4).get(0);
        transactions.executeWithoutResult(status -> {
            Monitor monitor = monitors.findById(oldClaim.monitorId()).orElseThrow();
            monitor.setCheckClaimedUntil(OffsetDateTime.now().minusSeconds(1));
        });

        MonitorTarget newClaim = checks.claimDueTargets(1, 4).get(0);
        assertThat(newClaim.monitorId()).isEqualTo(oldClaim.monitorId());
        assertThat(newClaim.claimToken()).isNotEqualTo(oldClaim.claimToken());

        checks.record(
                oldClaim.monitorId(),
                ProbeOutcome.connectionError(10, "stale").forClaim(oldClaim.claimToken()));
        Monitor afterStale = monitors.findById(oldClaim.monitorId()).orElseThrow();
        assertThat(afterStale.getCurrentState()).isEqualTo(MonitorState.UNKNOWN);
        assertThat(afterStale.getCheckClaimToken()).isEqualTo(newClaim.claimToken());

        checks.record(
                newClaim.monitorId(),
                ProbeOutcome.connectionError(10, "current").forClaim(newClaim.claimToken()));
        Monitor afterCurrent = monitors.findById(newClaim.monitorId()).orElseThrow();
        assertThat(afterCurrent.getCurrentState()).isEqualTo(MonitorState.DOWN);
        assertThat(afterCurrent.getCheckClaimToken()).isNull();
    }

    private List<MonitorTarget> claimAfter(CountDownLatch start, int limit) {
        await(start);
        return checks.claimDueTargets(limit, 4);
    }

    private Set<Long> ids(List<MonitorTarget> targets) {
        return targets.stream().map(MonitorTarget::monitorId).collect(java.util.stream.Collectors.toSet());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to start");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
