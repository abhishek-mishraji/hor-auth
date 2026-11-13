package com.handsoffretail.auth.bootstrap;

import com.handsoffretail.auth.audit.AuditAction;
import com.handsoffretail.auth.audit.AuditWriter;
import com.handsoffretail.auth.config.BootstrapProperties;
import com.handsoffretail.auth.password.PasswordHasher;
import com.handsoffretail.auth.password.PasswordRules;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class BootstrapService {
    private final BootstrapRepository repository;
    private final PasswordHasher passwordHasher;
    private final PasswordRules passwordRules;
    private final AuditWriter auditWriter;
    private final BootstrapProperties properties;
    private final Clock clock;

    public BootstrapService(BootstrapRepository repository, PasswordHasher passwordHasher,
            PasswordRules passwordRules, AuditWriter auditWriter, BootstrapProperties properties, Clock clock) {
        this.repository = repository;
        this.passwordHasher = passwordHasher;
        this.passwordRules = passwordRules;
        this.auditWriter = auditWriter;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public BootstrapResult initialize() {
        Long firstAdminId = repository.firstAdminId().orElse(null);
        boolean adminCreated = false;
        Instant now = clock.instant();
        if (firstAdminId == null) {
            BootstrapProperties.Admin admin = properties.admin();
            if (admin == null || !StringUtils.hasText(admin.email()) || !StringUtils.hasText(admin.password())) {
                throw new IllegalStateException(
                        "Bootstrap admin email and password are required when platform_admins is empty.");
            }
            String fullName = admin.fullName();
            if (!StringUtils.hasText(fullName)) {
                throw new IllegalStateException("Bootstrap admin full name is required.");
            }
            passwordRules.requireValid(admin.password());
            firstAdminId = repository.createAdmin(fullName, admin.email(), passwordHasher.hash(admin.password()), now);
            auditWriter.write(firstAdminId, AuditAction.ADMIN_CREATED, "ADMIN", firstAdminId,
                    Map.of("email", admin.email(), "fullName", fullName), now);
            adminCreated = true;
        }

        boolean storeCreated = false;
        if (!repository.hasStores()) {
            BootstrapProperties.DefaultStore store = properties.defaultStore();
            if (store == null) {
                throw new IllegalStateException("Bootstrap default store settings are required when stores is empty.");
            }
            long storeId = repository.createStore(new BootstrapStore(
                    store.storeName(), store.storeCode(), store.posVendor(), store.s3Folder(),
                    store.addressLine1(), store.city(), store.stateCode(), store.postalCode(),
                    store.countryCode(), store.timezone()), now);
            auditWriter.write(firstAdminId, AuditAction.STORE_CREATED, "STORE", storeId,
                    Map.of("storeCode", store.storeCode()), now);
            storeCreated = true;
        }
        return new BootstrapResult(adminCreated, storeCreated);
    }
}
