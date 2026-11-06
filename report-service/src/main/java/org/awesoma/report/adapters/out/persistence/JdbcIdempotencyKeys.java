package org.awesoma.report.adapters.out.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.awesoma.report.application.port.IdempotencyKeys;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class JdbcIdempotencyKeys implements IdempotencyKeys {
    private final JdbcTemplate db;

    @Override
    public Optional<IdempotencyRecord> find(long userId, long projectId, String key) {
        return db.query("""
                        SELECT user_id, project_id, idempotency_key, fingerprint, report_id, expires_at
                        FROM report_idempotency_keys WHERE user_id = ? AND project_id = ? AND idempotency_key = ?
                        """,
                (row, number) -> new IdempotencyRecord(row.getLong("user_id"), row.getLong("project_id"),
                        row.getString("idempotency_key"), row.getString("fingerprint"),
                        row.getObject("report_id", UUID.class), instant(row.getTimestamp("expires_at"))),
                userId, projectId, key).stream().findFirst();
    }

    /** A concurrent request with the same key waits for the first to commit, then finds the key taken. */
    @Override
    public boolean bind(IdempotencyRecord record) {
        return db.update("""
                        INSERT INTO report_idempotency_keys
                          (user_id, project_id, idempotency_key, fingerprint, report_id, expires_at)
                        VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                        """,
                record.userId(), record.projectId(), record.key(), record.fingerprint(), record.reportId(),
                record.expiresAt() == null ? null : Timestamp.from(record.expiresAt())) == 1;
    }

    @Override
    public boolean removeExpired(IdempotencyRecord observed, Instant now) {
        return db.update("""
                DELETE FROM report_idempotency_keys WHERE user_id = ? AND project_id = ? AND idempotency_key = ?
                AND report_id = ? AND expires_at = ? AND expires_at <= ?
                """, observed.userId(), observed.projectId(), observed.key(), observed.reportId(),
                Timestamp.from(observed.expiresAt()), Timestamp.from(now)) == 1;
    }

    @Override
    public void tombstone(UUID reportId, Instant expiresAt) {
        db.update("UPDATE report_idempotency_keys SET expires_at = ? WHERE report_id = ?",
                Timestamp.from(expiresAt), reportId);
    }

    @Override
    public int removeExpired(Instant now) {
        return db.update("DELETE FROM report_idempotency_keys WHERE expires_at <= ?", Timestamp.from(now));
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
