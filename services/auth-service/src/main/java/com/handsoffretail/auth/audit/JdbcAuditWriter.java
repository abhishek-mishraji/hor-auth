package com.handsoffretail.auth.audit;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JdbcAuditWriter implements AuditWriter {
    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public JdbcAuditWriter(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void write(long actorAdminId, AuditAction action, String targetType, long targetId,
            Map<String, Object> detail, Instant createdAt) {
        String detailJson = serialize(detail);
        jdbcClient.sql("""
                INSERT INTO core.audit_log (actor_admin_id, action, target_type, target_id, detail, created_at)
                VALUES (:actorAdminId, :action, :targetType, :targetId, CAST(:detail AS jsonb), :createdAt)
                """)
                .param("actorAdminId", actorAdminId)
                .param("action", action.name())
                .param("targetType", targetType)
                .param("targetId", targetId)
                .param("detail", detailJson)
                .param("createdAt", createdAt.atOffset(ZoneOffset.UTC))
                .update();
    }

    private String serialize(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize audit detail.", exception);
        }
    }
}
