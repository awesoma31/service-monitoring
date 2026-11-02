package org.awesoma.report;

import static org.assertj.core.api.Assertions.assertThat;

import org.awesoma.report.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;

class ReportServiceApplicationTest extends AbstractIntegrationTest {
    @Autowired private HealthEndpoint health;

    @Test
    void startsWithDatabaseAndBucket() {
        assertThat(health.healthForPath("db").getStatus()).isEqualTo(Status.UP);
        assertThat(health.healthForPath("minio").getStatus()).isEqualTo(Status.UP);
        assertThat(health.health().getStatus()).isEqualTo(Status.UP);
    }
}
