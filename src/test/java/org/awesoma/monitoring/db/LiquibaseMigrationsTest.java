package org.awesoma.monitoring.db;

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
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.awesoma.monitoring.support.PostgresContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LiquibaseMigrationsTest {

    private static final String CHANGELOG = "db/changelog/db.changelog-master.yaml";

    private static final List<String> DOMAIN_TABLES = List.of(
            "users", "projects", "project_members", "tags", "monitors",
            "monitor_tags", "check_results", "incidents", "channels", "notifications");

    private static final String OWN_DATABASE = "migrations_check";

    private static String jdbcUrl;

    @BeforeAll
    static void createOwnDatabase() throws Exception {
        dropOwnDatabase();
        runOnDefaultDatabase("CREATE DATABASE " + OWN_DATABASE);
        jdbcUrl = PostgresContainer.INSTANCE.getJdbcUrl()
                .replace("/" + PostgresContainer.INSTANCE.getDatabaseName(), "/" + OWN_DATABASE);
    }

    @AfterAll
    static void dropOwnDatabase() throws Exception {
        runOnDefaultDatabase(
                "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '"
                        + OWN_DATABASE + "'");
        runOnDefaultDatabase("DROP DATABASE IF EXISTS " + OWN_DATABASE);
    }

    private static void runOnDefaultDatabase(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                        PostgresContainer.INSTANCE.getJdbcUrl(),
                        PostgresContainer.INSTANCE.getUsername(),
                        PostgresContainer.INSTANCE.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    @Test
    void appliesEveryChangesetAndRollsBackCleanly() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                jdbcUrl,
                PostgresContainer.INSTANCE.getUsername(),
                PostgresContainer.INSTANCE.getPassword())) {

            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));

            try (Liquibase liquibase =
                    new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database)) {

                liquibase.update(new Contexts(), new LabelExpression());
                assertThat(tableNames(connection)).containsAll(DOMAIN_TABLES);

                int applied = countAppliedChangesets(connection);
                assertThat(applied).isEqualTo(liquibase.getDatabaseChangeLog().getChangeSets().size());

                liquibase.rollback(applied, new Contexts(), new LabelExpression());

                assertThat(tableNames(connection)).doesNotContainAnyElementsOf(DOMAIN_TABLES);
                assertThat(countAppliedChangesets(connection)).isZero();
            }
        }
    }

    private List<String> tableNames(Connection connection) throws Exception {
        List<String> names = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")) {
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        }
        return names;
    }

    private int countAppliedChangesets(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT count(*) FROM databasechangelog")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
