package org.awesoma.report.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.awesoma.report.support.Containers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Every changeset applies to an empty database and rolls back to nothing. */
class LiquibaseMigrationsTest {
    private static final String CHANGELOG = "db/changelog/db.changelog-master.yaml";
    private static final String OWN_DATABASE = "report_migrations_check";
    private static String jdbcUrl;

    @BeforeAll
    static void createOwnDatabase() throws Exception {
        dropOwnDatabase();
        runOnDefaultDatabase("CREATE DATABASE " + OWN_DATABASE);
        jdbcUrl = Containers.POSTGRES.getJdbcUrl()
                .replace("/" + Containers.POSTGRES.getDatabaseName(), "/" + OWN_DATABASE);
    }

    @AfterAll
    static void dropOwnDatabase() throws Exception {
        runOnDefaultDatabase("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '"
                + OWN_DATABASE + "'");
        runOnDefaultDatabase("DROP DATABASE IF EXISTS " + OWN_DATABASE);
    }

    @Test
    void appliesEveryChangesetAndRollsBackCleanly() throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, Containers.POSTGRES.getUsername(),
                Containers.POSTGRES.getPassword())) {
            var database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var liquibase = new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database)) {
                liquibase.update(new Contexts(), new LabelExpression());
                assertThat(tableNames(connection)).contains("reports", "report_idempotency_keys", "integration_inbox",
                        "deleted_projects");

                int applied = liquibase.getDatabaseChangeLog().getChangeSets().size();
                liquibase.rollback(applied, new Contexts(), new LabelExpression());

                assertThat(tableNames(connection)).doesNotContain("reports", "report_idempotency_keys",
                        "integration_inbox", "deleted_projects");
            }
        }
    }

    private static List<String> tableNames(Connection connection) throws Exception {
        List<String> names = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")) {
            while (rows.next()) names.add(rows.getString(1));
        }
        return names;
    }

    private static void runOnDefaultDatabase(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(Containers.POSTGRES.getJdbcUrl(),
                Containers.POSTGRES.getUsername(), Containers.POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
