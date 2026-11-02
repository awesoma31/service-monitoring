package org.awesoma.report.adapters.out.minio;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.awesoma.report.config.MinioProperties;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

/**
 * The bucket is created by minio-init, not by the service: its account may not create
 * buckets. Until the bucket exists the service reports DOWN and Compose keeps the gateway
 * waiting instead of letting uploads fail.
 */
@Component("minio")
@RequiredArgsConstructor
public class BucketHealthIndicator extends AbstractHealthIndicator {
    private final MinioClient minio;
    private final MinioProperties properties;

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        boolean exists = minio.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket()).build());
        (exists ? builder.up() : builder.down()).withDetail("bucket", properties.bucket());
    }
}
