package com.handsoff.hor.auth.admins;

import java.time.Instant;

public record PlatformAdminResponse(
        long id,
        String email,
        String fullName,
        String phoneNumber,
        String status,
        boolean locked,
        Instant lockedUntil,
        int failedLoginCount,
        Instant passwordChangedAt,
        Instant lastLoginAt,
        Instant createdAt) {
}
