package org.awesoma.user.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** One-time, transactional copy. Stable IDs and existing password hashes are preserved. */
@Slf4j
@Component
public class LegacyUserImporter {
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);

    private final DataSource destination;
    private final Duration waitTimeout;
    private final Duration pollInterval;

    @Autowired
    public LegacyUserImporter(DataSource destination,
            @Value("${security.migration.wait-timeout:PT5M}") Duration waitTimeout) {
        this(destination, waitTimeout, POLL_INTERVAL);
    }

    LegacyUserImporter(DataSource destination, Duration waitTimeout, Duration pollInterval) {
        this.destination = destination;
        this.waitTimeout = waitTimeout;
        this.pollInterval = pollInterval;
    }

    public void importFrom(String url, String username, String password) {
        if (url.isBlank()) return;
        try {
            awaitHashedSource(url, username, password);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for monitor-service migrations", interrupted);
        } catch (SQLException error) {
            throw new IllegalStateException("Cannot read the legacy users of monitor-service", error);
        }
        try (Connection target = destination.getConnection()) {
            target.setAutoCommit(false);
            try {
                try (var statement = target.createStatement()) {
                    statement.execute("SELECT pg_advisory_xact_lock(24103)");
                    try (var marker = statement.executeQuery("SELECT 1 FROM user_imports WHERE source = 'monitor-service'")) {
                        if (marker.next()) { target.rollback(); return; }
                    }
                }
                try (Connection source = DriverManager.getConnection(url, username, password)) {
                    source.setReadOnly(true);
                    source.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                    source.setAutoCommit(false);
                    String table = exists(source, "legacy_users") ? "legacy_users" : exists(source, "users") ? "users" : null;
                    if (table == null) { target.rollback(); return; }
                    int count = 0;
                    // Table names above are constants; values are always bound parameters.
                    try (var select = source.createStatement();
                         var rows = select.executeQuery("SELECT id, email, password_hash, full_name, status, role FROM " + table + " ORDER BY id");
                         var insert = target.prepareStatement("INSERT INTO users(id,email,password_hash,full_name,status,role) VALUES (?,?,?,?,?,?)")) {
                        while (rows.next()) {
                            insert.setLong(1, rows.getLong("id"));
                            insert.setString(2, rows.getString("email"));
                            String hash = rows.getString("password_hash");
                            if (!hash.startsWith("{pbkdf2@SpringSecurity_v5_8}")) {
                                throw new IllegalStateException("Apply monitor-service password migration before importing users");
                            }
                            insert.setString(3, hash);
                            insert.setString(4, rows.getString("full_name"));
                            insert.setString(5, rows.getString("status"));
                            insert.setString(6, rows.getString("role"));
                            insert.executeUpdate();
                            count++;
                        }
                    }
                    try (var statement = target.createStatement()) {
                        statement.execute("SELECT setval(pg_get_serial_sequence('users','id'), COALESCE((SELECT MAX(id) FROM users),1), EXISTS(SELECT 1 FROM users))");
                    }
                    try (var marker = target.prepareStatement("INSERT INTO user_imports(source,imported_count) VALUES ('monitor-service',?)")) {
                        marker.setInt(1, count);
                        marker.executeUpdate();
                    }
                    target.commit();
                }
            } catch (Exception error) {
                target.rollback();
                throw error;
            }
        } catch (Exception error) {
            throw new IllegalStateException("Cannot migrate legacy users; destination changes were rolled back", error);
        }
    }

    /**
     * When a stack from lab 2 is upgraded, monitor-service starts alongside this service and
     * its users still hold plaintext passwords until its migration 021 hashes them. Waiting
     * for that migration, instead of failing, lets both services come up in any order.
     */
    private void awaitHashedSource(String url, String username, String password)
            throws SQLException, InterruptedException {
        Instant deadline = Instant.now().plus(waitTimeout);
        while (true) {
            try (Connection source = DriverManager.getConnection(url, username, password)) {
                if (!exists(source, "users") || hasColumn(source, "users", "password_hash")) return;
            }
            if (Instant.now().isAfter(deadline)) {
                throw new IllegalStateException(
                        "monitor-service did not hash its user passwords within " + waitTimeout);
            }
            log.info("Waiting for monitor-service to hash user passwords before importing them");
            Thread.sleep(pollInterval.toMillis());
        }
    }

    private boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        try (var result = connection.getMetaData().getColumns(null, "public", table, column)) {
            return result.next();
        }
    }

    private boolean exists(Connection connection, String table) throws SQLException {
        try (var result = connection.getMetaData().getTables(null, "public", table, new String[] {"TABLE"})) {
            return result.next();
        }
    }
}
