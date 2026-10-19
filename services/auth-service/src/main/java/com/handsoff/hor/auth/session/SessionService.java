package com.handsoff.hor.auth.session;

import com.handsoff.hor.auth.account.Account;
import com.handsoff.hor.auth.account.AccountEmails;
import com.handsoff.hor.auth.account.AccountType;
import com.handsoff.hor.auth.account.AdminAccount;
import com.handsoff.hor.auth.account.StoreMembership;
import com.handsoff.hor.auth.account.StoreUserAccount;
import com.handsoff.hor.auth.client.DomainException;
import com.handsoff.hor.auth.common.AuthErrorCode;
import com.handsoff.hor.auth.config.AuthProperties;
import com.handsoff.hor.auth.password.PasswordHasher;
import com.handsoff.hor.auth.password.PasswordRules;
import com.handsoff.hor.auth.refresh.AdminRefreshTokenRepository;
import com.handsoff.hor.auth.refresh.RefreshTokenRecord;
import com.handsoff.hor.auth.refresh.RefreshTokenRepository;
import com.handsoff.hor.auth.refresh.UserRefreshTokenRepository;
import com.handsoff.hor.auth.token.AccessTokenIssuer;
import com.handsoff.hor.auth.token.RefreshTokenValue;
import com.handsoff.hor.auth.token.RefreshTokens;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {
    private final AccountEmails accountEmails;
    private final PasswordHasher passwordHasher;
    private final PasswordRules passwordRules;
    private final LoginAttempts loginAttempts;
    private final AdminRefreshTokenRepository adminTokens;
    private final UserRefreshTokenRepository userTokens;
    private final RefreshTokens refreshTokens;
    private final AccessTokenIssuer accessTokenIssuer;
    private final AuthProperties properties;
    private final Clock clock;
    private final SecurityEventLogger securityEvents;
    private final String dummyPasswordHash;

    public SessionService(AccountEmails accountEmails, PasswordHasher passwordHasher, PasswordRules passwordRules,
            LoginAttempts loginAttempts, AdminRefreshTokenRepository adminTokens, UserRefreshTokenRepository userTokens,
            RefreshTokens refreshTokens, AccessTokenIssuer accessTokenIssuer, AuthProperties properties,
            Clock clock, SecurityEventLogger securityEvents) {
        this.accountEmails = accountEmails;
        this.passwordHasher = passwordHasher;
        this.passwordRules = passwordRules;
        this.loginAttempts = loginAttempts;
        this.adminTokens = adminTokens;
        this.userTokens = userTokens;
        this.refreshTokens = refreshTokens;
        this.accessTokenIssuer = accessTokenIssuer;
        this.properties = properties;
        this.clock = clock;
        this.securityEvents = securityEvents;
        dummyPasswordHash = passwordHasher.hash(UUID.randomUUID().toString());
    }

    @Transactional
    public SessionResult login(String email, String password) {
        Optional<Account> found = accountEmails.findByEmail(email);
        if (found.isEmpty()) {
            passwordHasher.matches(password, dummyPasswordHash);
            securityEvents.failedLoginUnknown();
            throw new DomainException(AuthErrorCode.BAD_CREDENTIALS, "Email or password is incorrect.");
        }
        Account account = found.get();
        Instant now = clock.instant();
        if (isLocked(account, now)) {
            securityEvents.accountLocked(account);
            throw locked(account.lockedUntil());
        }
        if (!passwordHasher.matches(password, account.passwordHash())) {
            LoginFailureResult failure = loginAttempts.failed(account.accountType(), account.id());
            securityEvents.failedLogin(account);
            if (failure.failedLoginCount() >= properties.maxFailedLogins()) {
                securityEvents.accountLocked(account);
                throw locked(failure.lockedUntil());
            }
            throw new DomainException(AuthErrorCode.BAD_CREDENTIALS, "Email or password is incorrect.");
        }
        if ("DISABLED".equals(account.status())) {
            throw new DomainException(AuthErrorCode.ACCOUNT_DISABLED, "This account is disabled.");
        }
        if (account instanceof AdminAccount) {
            accountEmails.lockAdminRow(account.id());
            account = accountEmails.findById(account.accountType(), account.id())
                    .orElseThrow(SessionEndedException::new);
            now = clock.instant();
            if (isLocked(account, now)) {
                securityEvents.accountLocked(account);
                throw locked(account.lockedUntil());
            }
            if ("DISABLED".equals(account.status())) {
                throw new DomainException(AuthErrorCode.ACCOUNT_DISABLED, "This account is disabled.");
            }
        }
        loginAttempts.succeeded(account.accountType(), account.id());
        account = accountEmails.findById(account.accountType(), account.id())
                .orElseThrow(SessionEndedException::new);
        RefreshTokenRepository repository = repository(account.accountType());
        RefreshTokenValue refreshToken = refreshTokens.generate(account.accountType());
        Instant issuedAt = clock.instant();
        if (account.accountType() == AccountType.ADMIN) {
            repository.revokeAllActive(account.id(), issuedAt);
        }
        repository.insert(account.id(), refreshToken.hash(), issuedAt.plus(properties.refreshTokenTtl()), issuedAt);
        return sessionResult(account, refreshToken.value(), issuedAt);
    }

    @Transactional(noRollbackFor = SessionEndedException.class)
    public SessionResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null) {
            throw new SessionEndedException();
        }
        AccountType accountType = typeFromPrefix(rawRefreshToken);
        if (accountType == null) {
            throw new SessionEndedException();
        }
        RefreshTokenRepository repository = repository(accountType);
        Instant now = clock.instant();
        Optional<RefreshTokenRecord> tokenResult = repository.findByHash(refreshTokens.hash(rawRefreshToken));
        if (tokenResult.isEmpty()) {
            throw new SessionEndedException();
        }
        RefreshTokenRecord token = tokenResult.get();
        if (!token.expiresAt().isAfter(now)) {
            throw new SessionEndedException();
        }
        if (token.revokedAt() != null) {
            if (token.replacedBy() != null) {
                repository.revokeChainForward(token.tokenId(), now);
                accountEmails.findById(accountType, token.accountId()).ifPresent(securityEvents::refreshReuse);
            }
            throw new SessionEndedException();
        }
        Account account = accountEmails.findById(accountType, token.accountId()).orElse(null);
        if (account == null || "DISABLED".equals(account.status())) {
            repository.revokeChainForward(token.tokenId(), now);
            throw new SessionEndedException();
        }
        RefreshTokenValue replacement = refreshTokens.generate(accountType);
        long replacementId = repository.insert(account.id(), replacement.hash(),
                now.plus(properties.refreshTokenTtl()), now);
        if (!repository.rotate(token.tokenId(), replacementId, now)) {
            repository.deleteById(replacementId);
            RefreshTokenRecord current = repository.findByHash(token.tokenHash()).orElse(null);
            if (current != null && current.replacedBy() != null) {
                repository.revokeChainForward(current.tokenId(), now);
                securityEvents.refreshReuse(account);
            }
            throw new SessionEndedException();
        }
        Account refreshedAccount = accountEmails.findById(accountType, account.id()).orElse(account);
        return sessionResult(refreshedAccount, replacement.value(), now);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null) {
            return;
        }
        AccountType accountType = typeFromPrefix(rawRefreshToken);
        if (accountType == null) {
            return;
        }
        RefreshTokenRepository repository = repository(accountType);
        repository.findByHash(refreshTokens.hash(rawRefreshToken))
                .ifPresent(token -> repository.revokeChainForward(token.tokenId(), clock.instant()));
    }

    @Transactional(readOnly = true)
    public Me me(AccountType accountType, long id) {
        Account account = accountEmails.findById(accountType, id).orElseThrow(() ->
                new DomainException(AuthErrorCode.UNAUTHENTICATED, "Authentication is required."));
        return toMe(account);
    }

    @Transactional
    public SessionResult changePassword(AccountType accountType, long id, String currentPassword,
            String newPassword, String rawRefreshToken) {
        Account account = accountEmails.findById(accountType, id).orElseThrow(SessionEndedException::new);
        if (!passwordHasher.matches(currentPassword, account.passwordHash())) {
            throw new DomainException(AuthErrorCode.WRONG_PASSWORD, "Current password is incorrect.");
        }
        passwordRules.requireValid(newPassword);
        if ("DISABLED".equals(account.status())) {
            throw new SessionEndedException();
        }
        if (rawRefreshToken == null || typeFromPrefix(rawRefreshToken) != accountType) {
            throw new SessionEndedException();
        }
        RefreshTokenRepository repository = repository(accountType);
        Instant now = clock.instant();
        RefreshTokenRecord currentToken = repository.findByHash(refreshTokens.hash(rawRefreshToken))
                .filter(token -> token.accountId() == id && token.revokedAt() == null && token.expiresAt().isAfter(now))
                .orElseThrow(SessionEndedException::new);
        RefreshTokenValue replacement = refreshTokens.generate(accountType);
        long replacementId = repository.insert(id, replacement.hash(), now.plus(properties.refreshTokenTtl()), now);
        repository.revokeAllExcept(id, currentToken.tokenId(), now);
        accountEmails.updatePassword(accountType, id, passwordHasher.hash(newPassword), now);
        if (!repository.rotate(currentToken.tokenId(), replacementId, now)) {
            repository.deleteById(replacementId);
            throw new SessionEndedException();
        }
        Account changedAccount = accountEmails.findById(accountType, id).orElseThrow(SessionEndedException::new);
        return sessionResult(changedAccount, replacement.value(), now);
    }

    private SessionResult sessionResult(Account account, String refreshToken, Instant issuedAt) {
        return new SessionResult(toMe(account), accessTokenIssuer.issue(account, issuedAt), refreshToken);
    }

    private Me toMe(Account account) {
        boolean allStores = account.accountType() == AccountType.ADMIN;
        List<StoreMe> stores = account instanceof StoreUserAccount storeUser
                ? storeUser.stores().stream()
                        .map(store -> new StoreMe(store.storeId(), store.storeCode(), store.storeName(), store.storeRole()))
                        .toList()
                : List.of();
        return new Me(account.accountType().name(), account.id(), account.email(), account.fullName(), account.role(),
                allStores, stores, account.passwordChangedAt(), account.lastLoginAt());
    }

    private DomainException locked(Instant lockedUntil) {
        return new DomainException(AuthErrorCode.ACCOUNT_LOCKED, "Account is locked.",
                Map.of("lockedUntil", lockedUntil.toString()));
    }

    private boolean isLocked(Account account, Instant now) {
        return account.lockedUntil() != null && account.lockedUntil().isAfter(now);
    }

    private AccountType typeFromPrefix(String token) {
        if (token.startsWith("a.")) {
            return AccountType.ADMIN;
        }
        if (token.startsWith("u.")) {
            return AccountType.STORE_USER;
        }
        return null;
    }

    private RefreshTokenRepository repository(AccountType accountType) {
        return accountType == AccountType.ADMIN ? adminTokens : userTokens;
    }
}
