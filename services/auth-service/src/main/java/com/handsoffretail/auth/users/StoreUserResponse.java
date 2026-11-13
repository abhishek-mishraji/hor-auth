package com.handsoffretail.auth.users;

import java.time.Instant;
import java.util.List;

public record StoreUserResponse(
        long id,
        String userType,
        String role,
        String email,
        String fullName,
        String phoneNumber,
        String address,
        String status,
        boolean locked,
        Instant lockedUntil,
        int failedLoginCount,
        Instant passwordChangedAt,
        Instant lastLoginAt,
        Instant createdAt,
        long storeCount,
        List<UserStoreView> stores) {
    public StoreUserResponse {
        stores = stores == null ? null : List.copyOf(stores);
    }
}
