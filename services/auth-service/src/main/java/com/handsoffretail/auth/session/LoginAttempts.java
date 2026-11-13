package com.handsoffretail.auth.session;

import com.handsoffretail.auth.account.AccountType;
import com.handsoffretail.auth.config.AuthProperties;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginAttempts {
    private final LoginAttemptRepository repository;
    private final AuthProperties properties;
    private final Clock clock;

    public LoginAttempts(LoginAttemptRepository repository, AuthProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LoginFailureResult failed(AccountType accountType, long accountId) {
        Instant now = clock.instant();
        return repository.recordFailure(accountType, accountId, properties.maxFailedLogins(),
                now.plus(properties.lockDuration()));
    }

    public void succeeded(AccountType accountType, long accountId) {
        repository.recordSuccess(accountType, accountId, clock.instant());
    }
}
