package com.handsoff.hor.auth.users;

import java.time.Instant;

public record StoreUserView(
        long id,
        String userType,
        String email,
        String fullName,
        String phoneNumber,
        String address,
        String status,
        Instant lockedUntil,
        int failedLoginCount,
        Instant passwordChangedAt,
        Instant lastLoginAt,
        Instant createdAt,
        long storeCount) {
}
