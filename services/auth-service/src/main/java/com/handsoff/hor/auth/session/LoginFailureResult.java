package com.handsoff.hor.auth.session;

import java.time.Instant;

public record LoginFailureResult(int failedLoginCount, Instant lockedUntil) {
}
