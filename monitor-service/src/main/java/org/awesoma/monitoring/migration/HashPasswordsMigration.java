package org.awesoma.monitoring.migration;

import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;
import org.awesoma.security.PasswordHashing;

/** Runs once inside the Liquibase transaction, before the plaintext column is renamed. */
public class HashPasswordsMigration implements CustomTaskChange {
    @Override
    public void execute(Database database) throws CustomChangeException {
        var passwords = PasswordHashing.encoder();
        var connection = ((JdbcConnection) database.getConnection()).getUnderlyingConnection();
        try (var read = connection.prepareStatement("SELECT id, password FROM users");
             var rows = read.executeQuery();
             var write = connection.prepareStatement("UPDATE users SET password = ? WHERE id = ?")) {
            while (rows.next()) {
                write.setString(1, passwords.encode(rows.getString("password")));
                write.setLong(2, rows.getLong("id"));
                write.executeUpdate();
            }
        } catch (Exception error) {
            throw new CustomChangeException("Could not hash existing user passwords", error);
        }
    }

    @Override public String getConfirmationMessage() { return "Existing user passwords hashed"; }
    @Override public void setUp() {}
    @Override public void setFileOpener(ResourceAccessor accessor) {}
    @Override public ValidationErrors validate(Database database) { return new ValidationErrors(); }
}
