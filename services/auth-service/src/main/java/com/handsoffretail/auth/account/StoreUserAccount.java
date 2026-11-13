package com.handsoffretail.auth.account;

import java.time.Instant;
import java.util.List;

public record StoreUserAccount(
        long id,
        String userType,
        String email,
        String fullName,
        String phoneNumber,
        String address,
        String passwordHash,
        String status,
        int failedLoginCount,
        Instant lockedUntil,
        Instant passwordChangedAt,
        Instant lastLoginAt,
        List<StoreMembership> stores) implements Account {
    public StoreUserAccount {
        stores = List.copyOf(stores);
    }

    @Override
    public AccountType accountType() {
        return AccountType.STORE_USER;
    }

    @Override
    public String role() {
        return "STAFF".equals(userType) ? "HOR_STAFF" : "CLIENT_USER";
    }
}
