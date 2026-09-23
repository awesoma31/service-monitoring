package org.awesoma.monitoring.support;

import org.testcontainers.containers.PostgreSQLContainer;

public final class PostgresContainer {

    public static final PostgreSQLContainer<?> INSTANCE =
            new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        INSTANCE.start();
    }

    private PostgresContainer() {
    }
}
