package com.handsoff.hor.auth.audit;

import com.handsoff.hor.auth.client.DomainException;
import com.handsoff.hor.auth.common.AuthErrorCode;
import com.handsoff.hor.auth.common.PageResult;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditQueryService {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 500;
    private static final List<String> TARGET_TYPES = List.of("ADMIN", "USER", "STORE", "ASSIGNMENT");
    private final AuditRepository repository;

    public AuditQueryService(AuditRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResult<AuditLogResponse> list(Instant from, Instant to, String action, String targetType,
            Long targetId, Integer requestedLimit, Integer requestedOffset) {
        if (action != null && Arrays.stream(AuditAction.values()).noneMatch(value -> value.name().equals(action))) {
            throw new DomainException(AuthErrorCode.VALIDATION_FAILED, "Audit action is invalid.");
        }
        if (targetType != null && !TARGET_TYPES.contains(targetType)) {
            throw new DomainException(AuthErrorCode.VALIDATION_FAILED, "Audit target type is invalid.");
        }
        int limit = normalizeLimit(requestedLimit);
        int offset = Math.max(0, requestedOffset == null ? 0 : requestedOffset);
        return repository.findAll(from, to, action, targetType, targetId, limit, offset);
    }

    private int normalizeLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return DEFAULT_LIMIT;
        }
        if (requestedLimit < 0) {
            throw new DomainException(AuthErrorCode.VALIDATION_FAILED, "Limit must not be negative.");
        }
        return Math.min(requestedLimit, MAX_LIMIT);
    }
}
