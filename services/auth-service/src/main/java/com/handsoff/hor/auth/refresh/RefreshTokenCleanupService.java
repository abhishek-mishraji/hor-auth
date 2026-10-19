package com.handsoff.hor.auth.refresh;

import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenCleanupService {
    private static final Duration RETENTION = Duration.ofDays(30);
    private final AdminRefreshTokenRepository adminTokens;
    private final UserRefreshTokenRepository userTokens;
    private final Clock clock;

    public RefreshTokenCleanupService(AdminRefreshTokenRepository adminTokens,
            UserRefreshTokenRepository userTokens, Clock clock) {
        this.adminTokens = adminTokens;
        this.userTokens = userTokens;
        this.clock = clock;
    }

    @Transactional
    public void cleanExpiredTokens() {
        var cutoff = clock.instant().minus(RETENTION);
        adminTokens.deleteExpiredBefore(cutoff);
        userTokens.deleteExpiredBefore(cutoff);
    }
}
