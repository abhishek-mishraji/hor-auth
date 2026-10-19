package com.handsoff.hor.auth.users;

import com.handsoff.hor.auth.account.StoreUserAccount;
import com.handsoff.hor.auth.audit.AuditAction;
import com.handsoff.hor.auth.audit.AuditWriter;
import com.handsoff.hor.auth.client.DomainException;
import com.handsoff.hor.auth.common.AuthErrorCode;
import com.handsoff.hor.auth.common.ConstraintErrors;
import com.handsoff.hor.auth.common.PageResult;
import com.handsoff.hor.auth.password.PasswordHasher;
import com.handsoff.hor.auth.password.PasswordRules;
import com.handsoff.hor.auth.refresh.UserRefreshTokenRepository;
import com.handsoff.hor.auth.session.SecurityEventLogger;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreUserService {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 500;
    private final StoreUserRepository repository;
    private final PasswordHasher passwordHasher;
    private final PasswordRules passwordRules;
    private final UserRefreshTokenRepository refreshTokens;
    private final AuditWriter auditWriter;
    private final SecurityEventLogger securityEvents;
    private final Clock clock;

    public StoreUserService(StoreUserRepository repository, PasswordHasher passwordHasher, PasswordRules passwordRules,
            UserRefreshTokenRepository refreshTokens, AuditWriter auditWriter,
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
    public PageResult<StoreUserResponse> list(String userType, String status, String query,
            Integer requestedLimit, Integer requestedOffset) {
        validateFilter(userType, "USER_TYPE", List.of("STAFF", "CLIENT"));
        validateFilter(status, "STATUS", List.of("ACTIVE", "DISABLED"));
        int limit = normalizeLimit(requestedLimit);
        int offset = Math.max(0, requestedOffset == null ? 0 : requestedOffset);
        PageResult<StoreUserView> page = repository.findAll(userType, status, query, limit, offset);
        return new PageResult<>(page.items().stream().map(user -> response(user, null)).toList(),
                page.total(), limit, offset);
    }

    @Transactional(readOnly = true)
    public StoreUserResponse get(long id) {
        return response(find(id), repository.findStores(id));
    }

    @Transactional
    public StoreUserResponse create(CreateUserRequest request, long actorAdminId) {
        passwordRules.requireValid(request.password());
        Instant now = clock.instant();
        String phoneNumber = optionalValue(request.phoneNumber());
        long id;
        try {
            id = repository.create(request.userType(), request.email(), request.fullName(), phoneNumber,
                    request.address(), passwordHasher.hash(request.password()), actorAdminId, now);
        } catch (DataIntegrityViolationException exception) {
            throw ConstraintErrors.toDomainException(exception);
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("userType", request.userType());
        detail.put("email", request.email());
        detail.put("fullName", request.fullName());
        if (phoneNumber != null) {
            detail.put("phoneNumber", phoneNumber);
        }
        if (request.address() != null) {
            detail.put("address", request.address());
        }
        auditWriter.write(actorAdminId, AuditAction.USER_CREATED, "USER", id, detail, now);
        return get(id);
    }

    @Transactional
    public StoreUserResponse patch(long id, PatchUserRequest request, long actorAdminId) {
        StoreUserView current = find(id);
        boolean hasUserType = request.userType() != null;
        boolean hasEmail = request.email() != null;
        boolean hasFullName = request.fullName() != null;
        boolean hasPhone = request.phoneNumber() != null;
        boolean hasAddress = request.address() != null;
        boolean hasStatus = request.status() != null;
        if (hasStatus && (hasUserType || hasEmail || hasFullName || hasPhone || hasAddress)) {
            throw new DomainException(AuthErrorCode.STATUS_CHANGE_ALONE,
                    "Status must be changed without other fields.");
        }
        if (hasUserType) {
            throw new DomainException(AuthErrorCode.USER_TYPE_FIXED, "User type cannot be changed.");
        }
        if (!hasEmail && !hasFullName && !hasPhone && !hasAddress && !hasStatus) {
            return response(current, null);
        }
        Instant now = clock.instant();
        if (hasStatus) {
            changeStatus(id, current, request.status(), actorAdminId, now);
            return get(id);
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
            if (hasAddress) {
                String address = optionalValue(request.address());
                repository.changeAddress(id, address, now);
                detail.put("address", address);
            }
        } catch (DataIntegrityViolationException exception) {
            throw ConstraintErrors.toDomainException(exception);
        }
        auditWriter.write(actorAdminId, AuditAction.USER_UPDATED, "USER", id, detail, now);
        return get(id);
    }

    @Transactional
    public void resetPassword(long id, String newPassword, long actorAdminId) {
        StoreUserView user = find(id);
        passwordRules.requireValid(newPassword);
        Instant now = clock.instant();
        repository.resetPassword(id, passwordHasher.hash(newPassword), now);
        refreshTokens.revokeAll(id, now);
        securityEvents.sessionsRevoked("password reset", accountForLog(user));
        auditWriter.write(actorAdminId, AuditAction.USER_PASSWORD_RESET, "USER", id,
                Map.of("fields", List.of("password")), now);
    }

    @Transactional
    public void unlock(long id, long actorAdminId) {
        find(id);
        Instant now = clock.instant();
        repository.unlock(id, now);
        auditWriter.write(actorAdminId, AuditAction.USER_UNLOCKED, "USER", id, Map.of(), now);
    }

    private void changeStatus(long id, StoreUserView current, String status, long actorAdminId, Instant now) {
        validateFilter(status, "STATUS", List.of("ACTIVE", "DISABLED"));
        repository.changeStatus(id, status, now);
        AuditAction action = "DISABLED".equals(status) ? AuditAction.USER_DISABLED : AuditAction.USER_ENABLED;
        if ("DISABLED".equals(status)) {
            refreshTokens.revokeAll(id, now);
            securityEvents.sessionsRevoked("account disabled", accountForLog(current));
        }
        auditWriter.write(actorAdminId, action, "USER", id, Map.of("status", status), now);
    }

    private StoreUserView find(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new DomainException(AuthErrorCode.NOT_FOUND, "Store user not found."));
    }

    private StoreUserResponse response(StoreUserView user, List<UserStoreView> stores) {
        boolean locked = user.lockedUntil() != null && user.lockedUntil().isAfter(clock.instant());
        String role = "STAFF".equals(user.userType()) ? "HOR_STAFF" : "CLIENT_USER";
        return new StoreUserResponse(user.id(), user.userType(), role, user.email(), user.fullName(),
                user.phoneNumber(), user.address(), user.status(), locked, user.lockedUntil(),
                user.failedLoginCount(), user.passwordChangedAt(), user.lastLoginAt(), user.createdAt(),
                user.storeCount(), stores);
    }

    private void validateFilter(String value, String field, List<String> allowed) {
        if (value != null && !allowed.contains(value)) {
            throw new DomainException(ConstraintErrors.invalidFieldCode(field), "Invalid " + field + " value.");
        }
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

    private String optionalValue(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private StoreUserAccount accountForLog(StoreUserView user) {
        return new StoreUserAccount(user.id(), user.userType(), user.email(), user.fullName(), user.phoneNumber(),
                user.address(), "", user.status(), user.failedLoginCount(), user.lockedUntil(),
                user.passwordChangedAt(), user.lastLoginAt(), List.of());
    }
}
