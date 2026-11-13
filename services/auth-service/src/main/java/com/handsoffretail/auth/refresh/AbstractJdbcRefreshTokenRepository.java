package com.handsoffretail.auth.refresh;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

abstract class AbstractJdbcRefreshTokenRepository implements RefreshTokenRepository {
    private final JdbcClient jdbcClient;
    private final String table;
    private final String accountColumn;

    AbstractJdbcRefreshTokenRepository(JdbcClient jdbcClient, String table, String accountColumn) {
        this.jdbcClient = jdbcClient;
        this.table = table;
        this.accountColumn = accountColumn;
    }

    @Override
    public Optional<RefreshTokenRecord> findByHash(byte[] tokenHash) {
        return jdbcClient.sql("""
                SELECT token_id, %s AS account_id, token_hash, replaced_by, expires_at, revoked_at
                FROM %s WHERE token_hash = :tokenHash
                """.formatted(accountColumn, table))
                .param("tokenHash", tokenHash)
                .query((rs, rowNum) -> map(rs))
                .optional();
    }

    @Override
    public long insert(long accountId, byte[] tokenHash, Instant expiresAt, Instant createdAt) {
        return jdbcClient.sql("""
                INSERT INTO %s (%s, token_hash, expires_at, created_at)
                VALUES (:accountId, :tokenHash, :expiresAt, :createdAt)
                RETURNING token_id
                """.formatted(table, accountColumn))
                .param("accountId", accountId)
                .param("tokenHash", tokenHash)
                .param("expiresAt", expiresAt.atOffset(ZoneOffset.UTC))
                .param("createdAt", createdAt.atOffset(ZoneOffset.UTC))
                .query(Long.class)
                .single();
    }

    @Override
    public boolean rotate(long tokenId, long replacementId, Instant now) {
        return jdbcClient.sql("""
                UPDATE %s SET revoked_at = :now, replaced_by = :replacementId
                WHERE token_id = :tokenId AND revoked_at IS NULL
                """.formatted(table))
                .param("now", now.atOffset(ZoneOffset.UTC))
                .param("replacementId", replacementId)
                .param("tokenId", tokenId)
                .update() == 1;
    }

    @Override
    public void revokeById(long tokenId, Instant now) {
        jdbcClient.sql("UPDATE %s SET revoked_at = COALESCE(revoked_at, :now) WHERE token_id = :tokenId".formatted(table))
                .param("now", now.atOffset(ZoneOffset.UTC))
                .param("tokenId", tokenId)
                .update();
    }

    @Override
    public void revokeChainForward(long tokenId, Instant now) {
        jdbcClient.sql("""
                WITH RECURSIVE chain(token_id) AS (
                    SELECT token_id FROM %s WHERE token_id = :tokenId
                    UNION
                    SELECT next_token.token_id
                    FROM chain prior_token
                    JOIN %s previous_token ON previous_token.token_id = prior_token.token_id
                    JOIN %s next_token ON next_token.token_id = previous_token.replaced_by
                )
                UPDATE %s SET revoked_at = COALESCE(revoked_at, :now)
                WHERE token_id IN (SELECT token_id FROM chain)
                """.formatted(table, table, table, table))
                .param("tokenId", tokenId)
                .param("now", now.atOffset(ZoneOffset.UTC))
                .update();
    }

    @Override
    public void revokeAll(long accountId, Instant now) {
        jdbcClient.sql("UPDATE %s SET revoked_at = COALESCE(revoked_at, :now) WHERE %s = :accountId".formatted(table, accountColumn))
                .param("now", now.atOffset(ZoneOffset.UTC))
                .param("accountId", accountId)
                .update();
    }

    @Override
    public void revokeAllActive(long accountId, Instant now) {
        jdbcClient.sql("UPDATE %s SET revoked_at = :now WHERE %s = :accountId AND revoked_at IS NULL".formatted(table, accountColumn))
                .param("now", now.atOffset(ZoneOffset.UTC))
                .param("accountId", accountId)
                .update();
    }

    @Override
    public void revokeAllExcept(long accountId, long tokenId, Instant now) {
        jdbcClient.sql("""
                UPDATE %s SET revoked_at = :now
                WHERE %s = :accountId AND token_id <> :tokenId AND revoked_at IS NULL
                """.formatted(table, accountColumn))
                .param("now", now.atOffset(ZoneOffset.UTC))
                .param("accountId", accountId)
                .param("tokenId", tokenId)
                .update();
    }

    @Override
    public void deleteById(long tokenId) {
        jdbcClient.sql("DELETE FROM %s WHERE token_id = :tokenId".formatted(table))
                .param("tokenId", tokenId)
                .update();
    }

    @Override
    public int deleteExpiredBefore(Instant cutoff) {
        return jdbcClient.sql("""
                DELETE FROM %s WHERE expires_at < :cutoff OR revoked_at < :cutoff
                """.formatted(table))
                .param("cutoff", cutoff.atOffset(ZoneOffset.UTC))
                .update();
    }

    private RefreshTokenRecord map(ResultSet rs) throws SQLException {
        return new RefreshTokenRecord(rs.getLong("token_id"), rs.getLong("account_id"),
                rs.getBytes("token_hash"), nullableLong(rs, "replaced_by"),
                instant(rs, "expires_at"), instant(rs, "revoked_at"));
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
