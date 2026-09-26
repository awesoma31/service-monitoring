package org.awesoma.monitoring.support;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * One Postgres container for the whole test run.
 *
 * <p>Started from a static initialiser rather than through {@code @Container}, because the
 * JUnit extension ties a container's lifetime to the class that declares it — every test
 * class would then pay for its own database. Testcontainers' reaper stops this one when
 * the JVM exits.
 */
public final class PostgresContainer {

    public static final PostgreSQLContainer<?> INSTANCE =
            new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        INSTANCE.start();
    }

    private PostgresContainer() {
    }
}
