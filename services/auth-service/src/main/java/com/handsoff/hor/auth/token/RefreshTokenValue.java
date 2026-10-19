package com.handsoff.hor.auth.token;

public record RefreshTokenValue(String value, byte[] hash) {
    public RefreshTokenValue {
        hash = hash.clone();
    }

    @Override
    public byte[] hash() {
        return hash.clone();
    }
}
