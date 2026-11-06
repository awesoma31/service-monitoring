package org.awesoma.report.adapters.out.minio;

import static org.assertj.core.api.Assertions.assertThat;

import org.awesoma.report.config.MinioConfiguration;
import org.awesoma.report.support.AbstractIntegrationTest;
import org.awesoma.report.support.Containers;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

class BucketHealthIndicatorTest extends AbstractIntegrationTest {

    @Test
    void isUpWhenTheBucketExists() {
        var health = indicatorFor(BUCKET).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("bucket", BUCKET);
    }

    @Test
    void isDownUntilMinioInitCreatesTheBucket() {
        assertThat(indicatorFor("not-created-yet").health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void isDownWhenStorageRejectsTheCredentials() {
        var properties = storageProperties(Containers.MINIO.getS3URL(), BUCKET, "unknown", "wrong-secret");
        var indicator = new BucketHealthIndicator(new MinioConfiguration().minioClient(properties), properties);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }

    private BucketHealthIndicator indicatorFor(String bucket) {
        var properties = storageProperties(Containers.MINIO.getS3URL(), bucket,
                Containers.MINIO.getUserName(), Containers.MINIO.getPassword());
        return new BucketHealthIndicator(rootClient(), properties);
    }
}
