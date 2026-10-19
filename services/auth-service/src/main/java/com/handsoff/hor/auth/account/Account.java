package com.handsoff.hor.auth.account;

import java.time.Instant;

public sealed interface Account permits AdminAccount, StoreUserAccount {
    long id();

    String email();

    String fullName();

    String passwordHash();

    String status();

    int failedLoginCount();

    Instant lockedUntil();

    Instant passwordChangedAt();

    Instant lastLoginAt();

    AccountType accountType();

    String role();
}
