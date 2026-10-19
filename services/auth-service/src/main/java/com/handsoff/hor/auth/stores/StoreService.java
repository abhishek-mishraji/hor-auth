package com.handsoff.hor.auth.stores;

import com.handsoff.hor.auth.audit.AuditAction;
import com.handsoff.hor.auth.audit.AuditWriter;
import com.handsoff.hor.auth.client.DomainException;
import com.handsoff.hor.auth.common.AuthErrorCode;
import com.handsoff.hor.auth.common.ConstraintErrors;
import com.handsoff.hor.auth.common.PageResult;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreService {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 500;
    private static final List<String> STATUSES = List.of("ACTIVE", "INACTIVE");
    private static final List<String> POS_VENDORS = List.of("VERIFONE", "GILBARCO");
    private final StoreRepository repository;
    private final StoreRules rules;
    private final AuditWriter auditWriter;
    private final Clock clock;

    public StoreService(StoreRepository repository, StoreRules rules, AuditWriter auditWriter, Clock clock) {
        this.repository = repository;
        this.rules = rules;
        this.auditWriter = auditWriter;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResult<StoreResponse> list(String status, String posVendor, String query,
            Integer requestedLimit, Integer requestedOffset) {
        validateFilter(status, "STATUS", STATUSES);
        validateFilter(posVendor, "POS_VENDOR", POS_VENDORS);
        int limit = normalizeLimit(requestedLimit);
        int offset = Math.max(0, requestedOffset == null ? 0 : requestedOffset);
        PageResult<StoreView> page = repository.findAll(status, posVendor, query, limit, offset);
        return new PageResult<>(page.items().stream().map(this::response).toList(), page.total(), limit, offset);
    }

    @Transactional(readOnly = true)
    public StoreResponse get(long id) {
        return response(find(id));
    }

    @Transactional
    public StoreResponse create(CreateStoreRequest request, long actorAdminId) {
        String countryCode = request.countryCode() == null ? "US" : rules.uppercase(request.countryCode());
        String stateCode = rules.uppercase(request.stateCode());
        rules.validateTimezone(request.timezone());
        rules.validateS3Folder(request.s3Folder(), request.storeCode());
        rules.validatePostalCode(countryCode, request.postalCode());
        String contactNumber = emptyToNull(request.contactNumber());
        Instant now = clock.instant();
        long id;
        try {
            id = repository.create(request, stateCode, countryCode, contactNumber, now);
        } catch (DataIntegrityViolationException exception) {
            throw ConstraintErrors.toDomainException(exception);
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("storeCode", request.storeCode());
        detail.put("storeName", request.storeName());
        detail.put("posVendor", request.posVendor());
        detail.put("s3Folder", request.s3Folder());
        detail.put("addressLine1", request.addressLine1());
        if (request.addressLine2() != null && !request.addressLine2().isEmpty()) {
            detail.put("addressLine2", request.addressLine2());
        }
        detail.put("city", request.city());
        detail.put("stateCode", stateCode);
        detail.put("postalCode", request.postalCode());
        detail.put("countryCode", countryCode);
        if (contactNumber != null) {
            detail.put("contactNumber", contactNumber);
        }
        detail.put("timezone", request.timezone());
        auditWriter.write(actorAdminId, AuditAction.STORE_CREATED, "STORE", id, detail, now);
        return get(id);
    }

    @Transactional
    public StoreResponse patch(long id, PatchStoreRequest request, long actorAdminId) {
        StoreView current = find(id);
        boolean hasStoreCode = request.storeCode() != null;
        boolean hasPosVendor = request.posVendor() != null;
        boolean hasStoreName = request.storeName() != null;
        boolean hasS3Folder = request.s3Folder() != null;
        boolean hasAddressLine1 = request.addressLine1() != null;
        boolean hasAddressLine2 = request.addressLine2() != null;
        boolean hasCity = request.city() != null;
        boolean hasStateCode = request.stateCode() != null;
        boolean hasPostalCode = request.postalCode() != null;
        boolean hasCountryCode = request.countryCode() != null;
        boolean hasContactNumber = request.contactNumber() != null;
        boolean hasTimezone = request.timezone() != null;
        boolean hasStatus = request.status() != null;
        boolean hasOtherFields = hasStoreCode || hasPosVendor || hasStoreName || hasS3Folder || hasAddressLine1
                || hasAddressLine2 || hasCity || hasStateCode || hasPostalCode || hasCountryCode
                || hasContactNumber || hasTimezone;
        if (hasStatus && hasOtherFields) {
            throw new DomainException(AuthErrorCode.STATUS_CHANGE_ALONE,
                    "Status must be changed without other fields.");
        }
        if (hasStoreCode) {
            throw new DomainException(AuthErrorCode.STORE_CODE_FIXED, "Store code cannot be changed.");
        }
        if (hasPosVendor) {
            throw new DomainException(AuthErrorCode.POS_VENDOR_FIXED, "POS vendor cannot be changed.");
        }
        if (!hasStatus && !hasOtherFields) {
            return response(current);
        }
        Instant now = clock.instant();
        if (hasStatus) {
            changeStatus(id, current, request.status(), actorAdminId, now);
            return get(id);
        }
        String effectiveCountryCode = hasCountryCode ? rules.uppercase(request.countryCode()) : current.countryCode();
        String effectivePostalCode = hasPostalCode ? request.postalCode() : current.postalCode();
        String effectiveS3Folder = hasS3Folder ? request.s3Folder() : current.s3Folder();
        if (hasTimezone) {
            rules.validateTimezone(request.timezone());
        }
        if (hasS3Folder) {
            rules.validateS3Folder(effectiveS3Folder, current.storeCode());
        }
        if (hasCountryCode || hasPostalCode) {
            rules.validatePostalCode(effectiveCountryCode, effectivePostalCode);
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        try {
            if (hasStoreName) {
                repository.changeStoreName(id, request.storeName(), now);
                detail.put("storeName", request.storeName());
            }
            if (hasS3Folder) {
                repository.changeS3Folder(id, request.s3Folder(), now);
                detail.put("s3Folder", request.s3Folder());
            }
            if (hasAddressLine1) {
                repository.changeAddressLine1(id, request.addressLine1(), now);
                detail.put("addressLine1", request.addressLine1());
            }
            if (hasAddressLine2) {
                String value = emptyToNull(request.addressLine2());
                repository.changeAddressLine2(id, value, now);
                detail.put("addressLine2", value);
            }
            if (hasCity) {
                repository.changeCity(id, request.city(), now);
                detail.put("city", request.city());
            }
            if (hasStateCode) {
                String value = rules.uppercase(request.stateCode());
                repository.changeStateCode(id, value, now);
                detail.put("stateCode", value);
            }
            if (hasPostalCode) {
                repository.changePostalCode(id, request.postalCode(), now);
                detail.put("postalCode", request.postalCode());
            }
            if (hasCountryCode) {
                repository.changeCountryCode(id, effectiveCountryCode, now);
                detail.put("countryCode", effectiveCountryCode);
            }
            if (hasContactNumber) {
                String value = emptyToNull(request.contactNumber());
                repository.changeContactNumber(id, value, now);
                detail.put("contactNumber", value);
            }
            if (hasTimezone) {
                repository.changeTimezone(id, request.timezone(), now);
                detail.put("timezone", request.timezone());
            }
        } catch (DataIntegrityViolationException exception) {
            throw ConstraintErrors.toDomainException(exception);
        }
        auditWriter.write(actorAdminId, AuditAction.STORE_UPDATED, "STORE", id, detail, now);
        return get(id);
    }

    private void changeStatus(long id, StoreView current, String status, long actorAdminId, Instant now) {
        validateFilter(status, "STATUS", STATUSES);
        repository.changeStatus(id, status, now);
        AuditAction action = "INACTIVE".equals(status) ? AuditAction.STORE_DEACTIVATED : AuditAction.STORE_ACTIVATED;
        auditWriter.write(actorAdminId, action, "STORE", id, Map.of("status", status), now);
    }

    private StoreView find(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new DomainException(AuthErrorCode.NOT_FOUND, "Store not found."));
    }

    private StoreResponse response(StoreView store) {
        return new StoreResponse(store.id(), store.storeCode(), store.storeName(), store.posVendor(), store.s3Folder(),
                store.addressLine1(), store.addressLine2(), store.city(), store.stateCode(), store.postalCode(),
                store.countryCode(), store.contactNumber(), store.timezone(), store.status(), store.ownerName(),
                store.memberCount(), store.createdAt(), store.updatedAt());
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

    private String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
