package com.handsoffretail.auth.account;

import java.util.Optional;

public interface AccountEmails {
    Optional<Account> findByEmail(String email);

    Optional<Account> findById(AccountType accountType, long id);

    void lockAdminRow(long adminId);

    void updatePassword(AccountType accountType, long id, String passwordHash, java.time.Instant changedAt);
}
