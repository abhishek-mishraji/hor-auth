package com.handsoff.hor.auth.token;

import com.handsoff.hor.auth.account.Account;
import com.handsoff.hor.auth.account.AccountType;
import com.handsoff.hor.auth.account.StoreUserAccount;
import com.handsoff.hor.auth.config.AuthProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public final class JwtAccessTokenIssuer implements AccessTokenIssuer {
    private static final int MAX_STORE_COUNT = 300;
    private final SigningKeyStore keyStore;
    private final AuthProperties properties;

    public JwtAccessTokenIssuer(SigningKeyStore keyStore, AuthProperties properties) {
        this.keyStore = keyStore;
        this.properties = properties;
    }

    @Override
    public String issue(Account account, Instant issuedAt) {
        List<Long> storeIds = account instanceof StoreUserAccount storeUser
                ? storeUser.stores().stream().map(store -> store.storeId()).collect(Collectors.toList())
                : List.of();
        if (storeIds.size() > MAX_STORE_COUNT) {
            throw new IllegalStateException("Access token cannot be issued for an account assigned to more than 300 active stores.");
        }
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer("hor-auth")
                .subject(Long.toString(account.id()))
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .jwtID(UUID.randomUUID().toString())
                .claim("typ", account.accountType().name())
                .claim("role", account.role())
                .claim("name", account.fullName());
        if (account.accountType() == AccountType.ADMIN) {
            claims.claim("all_stores", true);
        } else {
            claims.claim("stores", storeIds);
        }
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyStore.keyId()).build();
        SignedJWT jwt = new SignedJWT(header, claims.build());
        try {
            jwt.sign(new RSASSASigner(keyStore.privateKey()));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Unable to sign access token.", exception);
        }
    }
}
