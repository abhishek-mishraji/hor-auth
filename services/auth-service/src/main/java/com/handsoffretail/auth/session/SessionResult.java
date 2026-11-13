package com.handsoffretail.auth.session;

public record SessionResult(Me me, String accessToken, String refreshToken) {
}
