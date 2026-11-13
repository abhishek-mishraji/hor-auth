package com.handsoffretail.auth.session;

import com.handsoffretail.auth.account.AccountType;
import java.time.Instant;

public interface LoginAttemptRepository {
    LoginFailureResult recordFailure(AccountType accountType, long accountId, int maxFailedLogins,
            Instant lockedUntilCandidate);

    void recordSuccess(AccountType accountType, long accountId, Instant now);
}
