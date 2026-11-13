package com.handsoffretail.auth.session;

import com.handsoffretail.auth.account.Account;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public final class SecurityEventLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityEventLogger.class);

    public void failedLogin(Account account) {
        LOGGER.warn("Security event: failed login account={}", identifier(account));
    }

    public void failedLoginUnknown() {
        LOGGER.warn("Security event: failed login account=unknown");
    }

    public void accountLocked(Account account) {
        LOGGER.warn("Security event: account locked account={}", identifier(account));
    }

    public void refreshReuse(Account account) {
        LOGGER.warn("Security event: refresh-token reuse detected account={}", identifier(account));
    }

    public void sessionsRevoked(String reason, Account account) {
        LOGGER.warn("Security event: sessions revoked reason={} account={}", reason, identifier(account));
    }

    private String identifier(Account account) {
        return account.accountType().name() + ":" + account.id();
    }
}
