package org.awesoma.user.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.awesoma.security.PasswordHashing;
import org.awesoma.user.support.PostgresContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class LegacyUserImporterIntegrationTest {
    private static final String SOURCE = "user_import_source";
    private static final String TARGET = "user_import_target";
    private static final String HASH = PasswordHashing.encoder().encode("legacy-password");
    private LegacyUserImporter importer;

    @BeforeEach
    void databases() throws Exception {
        cleanup();
        try (var connection = connect(null); var sql = connection.createStatement()) {
            sql.execute("CREATE DATABASE " + SOURCE);
            sql.execute("CREATE DATABASE " + TARGET);
        }
        try (var connection = connect(SOURCE); var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, email VARCHAR(255), password_hash VARCHAR(255), full_name VARCHAR(255), status VARCHAR(20), role VARCHAR(20))");
        }
        try (var connection = connect(TARGET)) {
            var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var liquibase = new Liquibase("db/changelog/db.changelog-master.yaml", new ClassLoaderResourceAccessor(), database)) {
                liquibase.update(new Contexts(), new LabelExpression());
            }
        }
        importer = new LegacyUserImporter(new DriverManagerDataSource(url(TARGET),
                PostgresContainer.INSTANCE.getUsername(), PostgresContainer.INSTANCE.getPassword()));
    }

    @AfterEach
    void cleanup() throws Exception {
        try (var connection = connect(null); var sql = connection.createStatement()) {
            sql.execute("DROP DATABASE IF EXISTS " + SOURCE + " WITH (FORCE)");
            sql.execute("DROP DATABASE IF EXISTS " + TARGET + " WITH (FORCE)");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void preservesIdsHashesRolesAndSequenceAndDoesNotImportTwice(boolean archived) throws Exception {
        sourceUser(42, "OPERATOR", HASH);
        if (archived) {
            try (var connection = connect(SOURCE); var sql = connection.createStatement()) { sql.execute("ALTER TABLE users RENAME TO legacy_users"); }
        }
        migrate();
        migrate();
        try (var connection = connect(TARGET); var sql = connection.createStatement();
             var rows = sql.executeQuery("SELECT id,password_hash,role FROM users")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getLong("id")).isEqualTo(42);
            assertThat(rows.getString("password_hash")).isEqualTo(HASH);
            assertThat(rows.getString("role")).isEqualTo("OPERATOR");
            assertThat(PasswordHashing.encoder().matches("legacy-password", rows.getString("password_hash"))).isTrue();
            assertThat(rows.next()).isFalse();
        }
        try (var connection = connect(TARGET); var sql = connection.createStatement();
             var next = sql.executeQuery("INSERT INTO users(email,password_hash,full_name) VALUES ('next@example.com','hash','Next') RETURNING id")) {
            assertThat(next.next()).isTrue();
            assertThat(next.getLong(1)).isEqualTo(43);
        }
    }

    @Test
    void rollsBackTheEntireImportOnAnIdCollision() throws Exception {
        sourceUser(1, "VIEWER", HASH);
        sourceUser(42, "SUPERVISOR", HASH);
        try (var connection = connect(TARGET); var sql = connection.createStatement()) {
            sql.execute("INSERT INTO users(id,email,password_hash,full_name) VALUES (42,'existing@example.com','existing-hash','Existing')");
        }
        assertThatThrownBy(this::migrate).isInstanceOf(IllegalStateException.class);
        try (var connection = connect(TARGET); var sql = connection.createStatement();
             var rows = sql.executeQuery("SELECT count(*) FROM users")) {
            rows.next();
            assertThat(rows.getLong(1)).isEqualTo(1);
        }
        try (var connection = connect(TARGET); var sql = connection.createStatement();
             var rows = sql.executeQuery("SELECT count(*) FROM user_imports")) {
            rows.next();
            assertThat(rows.getLong(1)).isZero();
        }
    }

    @Test
    void refusesUnhashedLegacyPasswordsAndAllowsAnEmptySourceOnFreshInstallation() throws Exception {
        sourceUser(1, "VIEWER", "plaintext-password");
        assertThatThrownBy(this::migrate).isInstanceOf(IllegalStateException.class);
        try (var connection = connect(SOURCE); var sql = connection.createStatement()) { sql.execute("DROP TABLE users"); }
        migrate();
        importer.importFrom("", "", "");
    }

    private void sourceUser(long id, String role, String hash) throws Exception {
        try (var connection = connect(SOURCE); var sql = connection.prepareStatement("INSERT INTO users VALUES (?, ?, ?, 'Legacy', 'ACTIVE', ?)")) {
            sql.setLong(1, id);
            sql.setString(2, "legacy-" + id + "@example.com");
            sql.setString(3, hash);
            sql.setString(4, role);
            sql.executeUpdate();
        }
    }
    private void migrate() {
        importer.importFrom(url(SOURCE), PostgresContainer.INSTANCE.getUsername(), PostgresContainer.INSTANCE.getPassword());
    }
    private String url(String database) {
        var pg = PostgresContainer.INSTANCE;
        return pg.getJdbcUrl().replace("/" + pg.getDatabaseName(), "/" + database);
    }
    private Connection connect(String database) throws Exception {
        var pg = PostgresContainer.INSTANCE;
        return DriverManager.getConnection(database == null ? pg.getJdbcUrl() : url(database), pg.getUsername(), pg.getPassword());
    }
}
