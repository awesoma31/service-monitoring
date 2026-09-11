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
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Migrations are only trustworthy if they also undo themselves: a rollback that fails
 * leaves a half-migrated database that nobody can move forward or back.
 */
@Testcontainers
class LiquibaseMigrationsTest {

    private static final String CHANGELOG = "db/changelog/db.changelog-master.yaml";

    private static final List<String> DOMAIN_TABLES = List.of(
            "users", "projects", "project_members", "tags", "monitors",
            "monitor_tags", "check_results", "incidents", "channels", "notifications");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void appliesEveryChangesetAndRollsBackCleanly() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {

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
