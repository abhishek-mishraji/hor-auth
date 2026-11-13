package com.handsoffretail.auth.refresh;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenCleanupJob {
    private final RefreshTokenCleanupService cleanupService;

    public RefreshTokenCleanupJob(RefreshTokenCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    public void cleanExpiredTokens() {
        cleanupService.cleanExpiredTokens();
    }
}
