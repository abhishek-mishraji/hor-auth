package com.handsoff.hor.auth.account;

import java.time.Instant;

public record AdminAccount(
        long id,
        String email,
        String fullName,
        String phoneNumber,
        String passwordHash,
        String status,
        int failedLoginCount,
        Instant lockedUntil,
        Instant passwordChangedAt,
        Instant lastLoginAt) implements Account {
    @Override
    public AccountType accountType() {
        return AccountType.ADMIN;
    }

    @Override
    public String role() {
        return "PLATFORM_ADMIN";
    }
}
