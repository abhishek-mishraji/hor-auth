package com.handsoff.hor.auth.audit;

import com.handsoff.hor.auth.common.PageResult;
import java.time.Instant;

public interface AuditRepository {
    PageResult<AuditLogResponse> findAll(Instant from, Instant to, String action, String targetType,
            Long targetId, int limit, int offset);
}
