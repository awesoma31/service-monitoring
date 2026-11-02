package org.awesoma.report.support;

import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One Postgres and one MinIO container for the whole test run, started from a static
 * initialiser so that test classes share them; Testcontainers' reaper stops both when the
 * JVM exits. MinIO is the same community build that Compose runs.
 */
public final class Containers {

    public static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    public static final MinIOContainer MINIO = new MinIOContainer(
            DockerImageName.parse("pgsty/minio:RELEASE.2026-08-04T00-00-00Z")
                    .asCompatibleSubstituteFor("minio/minio"));

    static {
        POSTGRES.start();
        MINIO.start();
    }

    private Containers() {
    }
}
