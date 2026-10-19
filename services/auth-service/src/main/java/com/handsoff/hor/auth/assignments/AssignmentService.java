package com.handsoff.hor.auth.assignments;

import com.handsoff.hor.auth.audit.AuditAction;
import com.handsoff.hor.auth.audit.AuditWriter;
import com.handsoff.hor.auth.client.DomainException;
import com.handsoff.hor.auth.common.AuthErrorCode;
import com.handsoff.hor.auth.common.ConstraintErrors;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignmentService {
    private final AssignmentRepository repository;
    private final AuditWriter auditWriter;
    private final Clock clock;

    public AssignmentService(AssignmentRepository repository, AuditWriter auditWriter, Clock clock) {
        this.repository = repository;
        this.auditWriter = auditWriter;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<StoreMemberResponse> members(long storeId) {
        requireStore(storeId);
        return repository.findMembers(storeId);
    }

    @Transactional
    public AssignmentResult assign(long storeId, long userId, String roleName, long actorAdminId) {
        requireStore(storeId);
        AssignmentUser user = repository.findUser(userId)
                .orElseThrow(() -> new DomainException(AuthErrorCode.NOT_FOUND, "Store user not found."));
        StoreRole role = parseRole(roleName);
        validateRole(user.userType(), role);
        Instant now = clock.instant();
        AssignmentRecord existing = repository.find(storeId, userId).orElse(null);
        if (existing != null && existing.storeRole().equals(role.name())) {
            return new AssignmentResult(member(storeId, userId), false);
        }
        if (existing == null) {
            try {
                repository.insert(storeId, user, role, actorAdminId, now);
            } catch (DataIntegrityViolationException exception) {
                throw ConstraintErrors.toDomainException(exception);
            }
            auditWriter.write(actorAdminId, AuditAction.STORE_ASSIGNED, "ASSIGNMENT", storeId,
                    Map.of("userId", userId, "storeRole", role.name()), now);
            return new AssignmentResult(member(storeId, userId), true);
        }
        try {
            repository.changeRole(storeId, userId, role, actorAdminId, now);
        } catch (DataIntegrityViolationException exception) {
            throw ConstraintErrors.toDomainException(exception);
        }
        auditWriter.write(actorAdminId, AuditAction.STORE_ASSIGNMENT_CHANGED, "ASSIGNMENT", storeId,
                Map.of("userId", userId, "storeRole", role.name(), "previousRole", existing.storeRole()), now);
        return new AssignmentResult(member(storeId, userId), false);
    }

    @Transactional
    public void unassign(long storeId, long userId, long actorAdminId) {
        requireStore(storeId);
        AssignmentRecord assignment = repository.find(storeId, userId)
                .orElseThrow(() -> new DomainException(AuthErrorCode.NOT_FOUND, "Store assignment not found."));
        if (!repository.delete(storeId, userId)) {
            throw new DomainException(AuthErrorCode.NOT_FOUND, "Store assignment not found.");
        }
        auditWriter.write(actorAdminId, AuditAction.STORE_UNASSIGNED, "ASSIGNMENT", storeId,
                Map.of("userId", userId, "storeRole", assignment.storeRole()), clock.instant());
    }

    private void requireStore(long storeId) {
        if (!repository.storeExists(storeId)) {
            throw new DomainException(AuthErrorCode.NOT_FOUND, "Store not found.");
        }
    }

    private StoreRole parseRole(String roleName) {
        try {
            return StoreRole.valueOf(roleName);
        } catch (IllegalArgumentException exception) {
            throw new DomainException(AuthErrorCode.INVALID_STORE_ROLE, "Store role is invalid.");
        }
    }

    private void validateRole(String userType, StoreRole role) {
        boolean valid = "STAFF".equals(userType)
                ? role == StoreRole.STAFF
                : role == StoreRole.OWNER || role == StoreRole.PARTNER;
        if (!valid) {
            throw new DomainException(AuthErrorCode.INVALID_STORE_ROLE, "Store role is invalid for this user type.");
        }
    }

    private StoreMemberResponse member(long storeId, long userId) {
        return repository.findMembers(storeId).stream()
                .filter(member -> member.userId() == userId)
                .findFirst()
                .orElseThrow(() -> new DomainException(AuthErrorCode.NOT_FOUND, "Store assignment not found."));
    }
}
