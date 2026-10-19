package com.handsoff.hor.auth.refresh;

import java.time.Instant;

public record RefreshTokenRecord(
        long tokenId,
        long accountId,
        byte[] tokenHash,
        Long replacedBy,
        Instant expiresAt,
        Instant revokedAt) {
    public RefreshTokenRecord {
        tokenHash = tokenHash.clone();
    }

    @Override
    public byte[] tokenHash() {
        return tokenHash.clone();
    }
}
