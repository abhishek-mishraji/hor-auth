package com.handsoffretail.auth.admins;

import java.time.Instant;

public record PlatformAdminView(
        long id,
        String email,
        String fullName,
        String phoneNumber,
        String status,
        Instant lockedUntil,
        int failedLoginCount,
        Instant passwordChangedAt,
        Instant lastLoginAt,
        Instant createdAt) {
}
