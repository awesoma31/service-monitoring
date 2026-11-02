package org.awesoma.report.support;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Boots the application against the shared Postgres and MinIO. Tests talk to MinIO with the
 * root account; the separate service account is a Compose concern checked by minio-init.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {
    protected static final String BUCKET = "reports-test";

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", Containers.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", Containers.POSTGRES::getUsername);
        registry.add("spring.datasource.password", Containers.POSTGRES::getPassword);
        registry.add("storage.minio.endpoint", Containers.MINIO::getS3URL);
        registry.add("storage.minio.bucket", () -> BUCKET);
        registry.add("storage.minio.access-key", Containers.MINIO::getUserName);
        registry.add("storage.minio.secret-key", Containers.MINIO::getPassword);
    }

    @BeforeAll
    static void createBucket() throws Exception {
        MinioClient root = rootClient();
        if (!root.bucketExists(BucketExistsArgs.builder().bucket(BUCKET).build())) {
            root.makeBucket(MakeBucketArgs.builder().bucket(BUCKET).build());
        }
    }

    protected static MinioClient rootClient() {
        return MinioClient.builder()
                .endpoint(Containers.MINIO.getS3URL())
                .credentials(Containers.MINIO.getUserName(), Containers.MINIO.getPassword())
                .build();
    }
}
