package com.handsoff.hor.auth.session;

public record SessionResult(Me me, String accessToken, String refreshToken) {
}
