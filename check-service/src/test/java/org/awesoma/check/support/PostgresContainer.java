package org.awesoma.check.support;

import org.testcontainers.containers.PostgreSQLContainer;

/** One Postgres container for the whole test run, as in monitor-service. */
public final class PostgresContainer {

    public static final PostgreSQLContainer<?> INSTANCE =
            new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        INSTANCE.start();
    }

    private PostgresContainer() {
    }
}
