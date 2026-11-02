package org.awesoma.report.support;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.awesoma.report.adapters.out.http.ProjectLookupClient;
import org.awesoma.report.adapters.out.http.ReportDataClients;
import org.awesoma.report.config.MinioProperties;
import org.awesoma.security.access.ResourceAccessClient;
import org.awesoma.security.support.TestJwtConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Boots the application against the shared Postgres and MinIO. Tests talk to MinIO with the
 * root account; the separate service account is a Compose concern checked by minio-init.
 * monitor-service is replaced by mocks of its two clients: every project exists unless listed
 * as missing, and a user has only the access a test grants.
 */
@SpringBootTest
@Import(TestJwtConfiguration.class)
public abstract class AbstractIntegrationTest {
    protected static final String BUCKET = "reports-test";

    @MockitoBean protected ProjectLookupClient projectLookup;
    @MockitoBean protected ResourceAccessClient resourceAccess;
    @MockitoBean protected ReportDataClients.MonitorData monitorData;
    @MockitoBean protected ReportDataClients.CheckData checkData;
    protected final Set<Long> missingProjects = new HashSet<>();
    /** Project → user → may write. */
    private final Map<Long, Map<Long, Boolean>> grants = new HashMap<>();

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", Containers.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", Containers.POSTGRES::getUsername);
        registry.add("spring.datasource.password", Containers.POSTGRES::getPassword);
        registry.add("storage.minio.endpoint", Containers.MINIO::getS3URL);
        registry.add("storage.minio.bucket", () -> BUCKET);
        registry.add("storage.minio.access-key", Containers.MINIO::getUserName);
        registry.add("storage.minio.secret-key", Containers.MINIO::getPassword);
        // Tests run the background work themselves, so no scheduled pass races their assertions.
        registry.add("report.jobs.enabled", () -> false);
    }

    @BeforeAll
    static void createBucket() throws Exception {
        MinioClient root = rootClient();
        if (!root.bucketExists(BucketExistsArgs.builder().bucket(BUCKET).build())) {
            root.makeBucket(MakeBucketArgs.builder().bucket(BUCKET).build());
        }
    }

    @BeforeEach
    void answerAsMonitorService() {
        when(projectLookup.projectExists(anyLong()))
                .thenAnswer(call -> !missingProjects.contains(call.<Long>getArgument(0)));
        when(resourceAccess.allowed(eq("project"), anyLong(), anyLong(), anyBoolean())).thenAnswer(call -> {
            Boolean mayWrite = grants.getOrDefault(call.<Long>getArgument(1), Map.of()).get(call.<Long>getArgument(2));
            return mayWrite != null && (mayWrite || !call.<Boolean>getArgument(3));
        });
    }

    protected void grantRead(long projectId, long userId) {
        grants.computeIfAbsent(projectId, id -> new HashMap<>()).put(userId, false);
    }

    protected void grantWrite(long projectId, long userId) {
        grants.computeIfAbsent(projectId, id -> new HashMap<>()).put(userId, true);
    }

    protected static MinioClient rootClient() {
        return MinioClient.builder()
                .endpoint(Containers.MINIO.getS3URL())
                .credentials(Containers.MINIO.getUserName(), Containers.MINIO.getPassword())
                .build();
    }

    protected static MinioProperties storageProperties(String endpoint, String bucket, String accessKey,
            String secretKey) {
        return new MinioProperties(endpoint, bucket, accessKey, secretKey, Duration.ofSeconds(2),
                Duration.ofSeconds(10));
    }
}
