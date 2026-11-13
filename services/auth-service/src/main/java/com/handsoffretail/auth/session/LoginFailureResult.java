package com.handsoffretail.auth.session;

import java.time.Instant;

public record LoginFailureResult(int failedLoginCount, Instant lockedUntil) {
}
