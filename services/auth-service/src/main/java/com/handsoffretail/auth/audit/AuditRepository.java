package com.handsoffretail.auth.audit;

import com.handsoffretail.auth.common.PageResult;
import java.time.Instant;

public interface AuditRepository {
    PageResult<AuditLogResponse> findAll(Instant from, Instant to, String action, String targetType,
            Long targetId, int limit, int offset);
}
