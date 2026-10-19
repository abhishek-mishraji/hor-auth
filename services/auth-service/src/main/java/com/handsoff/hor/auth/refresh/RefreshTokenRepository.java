package com.handsoff.hor.auth.refresh;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository {
    Optional<RefreshTokenRecord> findByHash(byte[] tokenHash);

    long insert(long accountId, byte[] tokenHash, Instant expiresAt, Instant createdAt);

    boolean rotate(long tokenId, long replacementId, Instant now);

    void revokeById(long tokenId, Instant now);

    void revokeChainForward(long tokenId, Instant now);

    void revokeAll(long accountId, Instant now);

    void revokeAllActive(long accountId, Instant now);

    void revokeAllExcept(long accountId, long tokenId, Instant now);

    void deleteById(long tokenId);

    int deleteExpiredBefore(Instant cutoff);
}
