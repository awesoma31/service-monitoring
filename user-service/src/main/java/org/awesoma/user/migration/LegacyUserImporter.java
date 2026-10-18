package org.awesoma.user.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/** One-time, transactional copy. Stable IDs and existing password hashes are preserved. */
@Component
public class LegacyUserImporter {
    private final DataSource destination;

    public LegacyUserImporter(DataSource destination) {
        this.destination = destination;
    }

    public void importFrom(String url, String username, String password) {
        if (url.isBlank()) return;
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

    private boolean exists(Connection connection, String table) throws Exception {
        try (var result = connection.getMetaData().getTables(null, "public", table, new String[] {"TABLE"})) {
            return result.next();
        }
    }
}
