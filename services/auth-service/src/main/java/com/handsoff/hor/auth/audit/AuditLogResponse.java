package com.handsoff.hor.auth.audit;

import java.time.Instant;
import tools.jackson.databind.JsonNode;

public record AuditLogResponse(
        long id,
        Instant createdAt,
        long actorAdminId,
        String actorName,
        String action,
        String targetType,
        long targetId,
        String targetLabel,
        JsonNode detail) {
}
