package com.handsoff.hor.auth.audit;

import java.time.Instant;
import java.util.Map;

public interface AuditWriter {
    void write(long actorAdminId, AuditAction action, String targetType, long targetId,
            Map<String, Object> detail, Instant createdAt);
}
