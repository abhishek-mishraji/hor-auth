package com.handsoffretail.auth.admins;

import com.handsoffretail.auth.audit.AuditAction;
import com.handsoffretail.auth.audit.AuditWriter;
import com.handsoffretail.auth.client.DomainException;
import com.handsoffretail.auth.common.AuthErrorCode;
import com.handsoffretail.auth.common.ConstraintErrors;
import com.handsoffretail.auth.common.PageResult;
import com.handsoffretail.auth.password.PasswordHasher;
import com.handsoffretail.auth.password.PasswordRules;
import com.handsoffretail.auth.refresh.AdminRefreshTokenRepository;
import com.handsoffretail.auth.session.SecurityEventLogger;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 500;
    private final AdminRepository repository;
    private final PasswordHasher passwordHasher;
    private final PasswordRules passwordRules;
    private final AdminRefreshTokenRepository refreshTokens;
    private final AuditWriter auditWriter;
    private final SecurityEventLogger securityEvents;
    private final Clock clock;

    public AdminService(AdminRepository repository, PasswordHasher passwordHasher, PasswordRules passwordRules,
            AdminRefreshTokenRepository refreshTokens, AuditWriter auditWriter,
            SecurityEventLogger securityEvents, Clock clock) {
        this.repository = repository;
        this.passwordHasher = passwordHasher;
        this.passwordRules = passwordRules;
        this.refreshTokens = refreshTokens;
        this.auditWriter = auditWriter;
        this.securityEvents = securityEvents;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResult<PlatformAdminResponse> list(String status, String query, Integer requestedLimit, Integer requestedOffset) {
        int limit = normalizeLimit(requestedLimit);
        int offset = Math.max(0, requestedOffset == null ? 0 : requestedOffset);
        PageResult<PlatformAdminView> page = repository.findAll(status, query, limit, offset, clock.instant());
        return new PageResult<>(page.items().stream().map(this::response).toList(), page.total(), limit, offset);
    }

    @Transactional(readOnly = true)
    public PlatformAdminResponse get(long id) {
        return response(find(id));
    }

    @Transactional
    public PlatformAdminResponse create(CreateAdminRequest request, long actorAdminId) {
        passwordRules.requireValid(request.password());
        Instant now = clock.instant();
        String phoneNumber = optionalValue(request.phoneNumber());
        long id;
        try {
            id = repository.create(request.email(), request.fullName(), phoneNumber,
                    passwordHasher.hash(request.password()), now);
        } catch (DataIntegrityViolationException exception) {
            throw ConstraintErrors.toDomainException(exception);
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("email", request.email());
        detail.put("fullName", request.fullName());
        if (phoneNumber != null) {
            detail.put("phoneNumber", phoneNumber);
        }
        auditWriter.write(actorAdminId, AuditAction.ADMIN_CREATED, "ADMIN", id, detail, now);
        return response(find(id));
    }

    @Transactional
    public PlatformAdminResponse patch(long id, PatchAdminRequest request, long actorAdminId) {
        PlatformAdminView current = find(id);
        boolean hasEmail = request.email() != null;
        boolean hasFullName = request.fullName() != null;
        boolean hasPhone = request.phoneNumber() != null;
        boolean hasStatus = request.status() != null;
        if (hasStatus && (hasEmail || hasFullName || hasPhone)) {
            throw new DomainException(AuthErrorCode.STATUS_CHANGE_ALONE,
                    "Status must be changed without other fields.");
        }
        if (!hasStatus && !hasEmail && !hasFullName && !hasPhone) {
            return response(current);
        }
        Instant now = clock.instant();
        if (hasStatus) {
            changeStatus(id, current, request.status(), actorAdminId, now);
            return response(find(id));
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        try {
            if (hasEmail) {
                repository.changeEmail(current.email(), request.email());
                detail.put("email", request.email());
            }
            if (hasFullName) {
                repository.changeFullName(id, request.fullName(), now);
                detail.put("fullName", request.fullName());
            }
            if (hasPhone) {
                String phoneNumber = optionalValue(request.phoneNumber());
                repository.changePhoneNumber(id, phoneNumber, now);
                detail.put("phoneNumber", phoneNumber);
            }
        } catch (DataIntegrityViolationException exception) {
            throw ConstraintErrors.toDomainException(exception);
        }
        auditWriter.write(actorAdminId, AuditAction.ADMIN_UPDATED, "ADMIN", id, detail, now);
        return response(find(id));
    }

    @Transactional
    public void resetPassword(long id, String newPassword, long actorAdminId) {
        PlatformAdminView admin = find(id);
        passwordRules.requireValid(newPassword);
        Instant now = clock.instant();
        repository.resetPassword(id, passwordHasher.hash(newPassword), now);
        refreshTokens.revokeAll(id, now);
        securityEvents.sessionsRevoked("password reset", accountForLog(admin));
        auditWriter.write(actorAdminId, AuditAction.ADMIN_PASSWORD_RESET, "ADMIN", id,
                Map.of("fields", List.of("password")), now);
    }

    @Transactional
    public void unlock(long id, long actorAdminId) {
        find(id);
        Instant now = clock.instant();
        repository.unlock(id, now);
        auditWriter.write(actorAdminId, AuditAction.ADMIN_UNLOCKED, "ADMIN", id, Map.of(), now);
    }

    private void changeStatus(long id, PlatformAdminView current, String status, long actorAdminId, Instant now) {
        if (!"ACTIVE".equals(status) && !"DISABLED".equals(status)) {
            throw new DomainException(ConstraintErrors.invalidFieldCode("STATUS"), "Status must be ACTIVE or DISABLED.");
        }
        if ("DISABLED".equals(status)) {
            repository.lockActiveRows();
            current = find(id);
            if (id == actorAdminId) {
                throw new DomainException(AuthErrorCode.CANNOT_DISABLE_SELF, "An admin cannot disable themself.");
            }
            if ("ACTIVE".equals(current.status()) && repository.countActive() <= 1) {
                throw new DomainException(AuthErrorCode.LAST_ACTIVE_ADMIN, "The last active platform admin cannot be disabled.");
            }
        }
        repository.changeStatus(id, status, now);
        AuditAction action = "DISABLED".equals(status) ? AuditAction.ADMIN_DISABLED : AuditAction.ADMIN_ENABLED;
        if ("DISABLED".equals(status)) {
            refreshTokens.revokeAll(id, now);
            securityEvents.sessionsRevoked("account disabled", accountForLog(current));
        }
        auditWriter.write(actorAdminId, action, "ADMIN", id, Map.of("status", status), now);
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

    private PlatformAdminView find(long id) {
        return repository.findById(id, clock.instant())
                .orElseThrow(() -> new DomainException(AuthErrorCode.NOT_FOUND, "Platform admin not found."));
    }

    private PlatformAdminResponse response(PlatformAdminView admin) {
        boolean locked = admin.lockedUntil() != null && admin.lockedUntil().isAfter(clock.instant());
        return new PlatformAdminResponse(admin.id(), admin.email(), admin.fullName(), admin.phoneNumber(),
                admin.status(), locked, admin.lockedUntil(), admin.failedLoginCount(),
                admin.passwordChangedAt(), admin.lastLoginAt(), admin.createdAt());
    }

    private String optionalValue(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private com.handsoffretail.auth.account.AdminAccount accountForLog(PlatformAdminView admin) {
        return new com.handsoffretail.auth.account.AdminAccount(admin.id(), admin.email(), admin.fullName(),
                admin.phoneNumber(), "", admin.status(), admin.failedLoginCount(), admin.lockedUntil(),
                admin.passwordChangedAt(), admin.lastLoginAt());
    }
}
