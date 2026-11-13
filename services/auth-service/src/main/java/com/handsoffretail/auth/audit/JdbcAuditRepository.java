package com.handsoffretail.auth.audit;

import com.handsoffretail.auth.common.PageResult;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JdbcAuditRepository implements AuditRepository {
    private static final String FILTERS = "WHERE (CAST(:fromTime AS timestamptz) IS NULL OR al.created_at >= :fromTime) "
            + "AND (CAST(:toTime AS timestamptz) IS NULL OR al.created_at < :toTime) "
            + "AND (CAST(:action AS text) IS NULL OR al.action = :action) "
            + "AND (CAST(:targetType AS text) IS NULL OR al.target_type = :targetType) "
            + "AND (CAST(:targetId AS bigint) IS NULL OR al.target_id = :targetId)";
    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public JdbcAuditRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public PageResult<AuditLogResponse> findAll(Instant from, Instant to, String action, String targetType,
            Long targetId, int limit, int offset) {
        long total = bindFilters(jdbcClient.sql("SELECT count(*) FROM core.audit_log al " + FILTERS),
                from, to, action, targetType, targetId).query(Long.class).single();
        List<AuditLogResponse> items = bindFilters(jdbcClient.sql("""
                SELECT al.audit_id, al.created_at, al.actor_admin_id, actor.full_name AS actor_name,
                       al.action, al.target_type, al.target_id, al.detail::text AS detail,
                       CASE al.target_type
                           WHEN 'ADMIN' THEN (SELECT pa.full_name FROM core.platform_admins pa WHERE pa.admin_id = al.target_id)
                           WHEN 'USER' THEN (SELECT su.full_name FROM core.store_users su WHERE su.user_id = al.target_id)
                           WHEN 'STORE' THEN (SELECT s.store_code FROM core.stores s WHERE s.store_id = al.target_id)
                           WHEN 'ASSIGNMENT' THEN (SELECT s.store_code FROM core.stores s WHERE s.store_id = al.target_id)
                       END AS target_label
                FROM core.audit_log al
                JOIN core.platform_admins actor ON actor.admin_id = al.actor_admin_id
                """ + FILTERS + " ORDER BY al.created_at DESC, al.audit_id DESC LIMIT :limit OFFSET :offset"),
                from, to, action, targetType, targetId)
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, rowNum) -> map(rs))
                .list();
        return new PageResult<>(items, total, limit, offset);
    }

    private JdbcClient.StatementSpec bindFilters(JdbcClient.StatementSpec statement, Instant from, Instant to,
            String action, String targetType, Long targetId) {
        return statement
                .param("fromTime", offset(from))
                .param("toTime", offset(to))
                .param("action", action)
                .param("targetType", targetType)
                .param("targetId", targetId);
    }

    private OffsetDateTime offset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private AuditLogResponse map(ResultSet rs) throws SQLException {
        try {
            JsonNode detail = objectMapper.readTree(rs.getString("detail"));
            return new AuditLogResponse(rs.getLong("audit_id"), instant(rs, "created_at"),
                    rs.getLong("actor_admin_id"), rs.getString("actor_name"), rs.getString("action"),
                    rs.getString("target_type"), rs.getLong("target_id"), rs.getString("target_label"), detail);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to read audit detail.", exception);
        }
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
