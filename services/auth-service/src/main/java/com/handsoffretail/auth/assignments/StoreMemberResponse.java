package com.handsoffretail.auth.assignments;

import java.time.Instant;

public record StoreMemberResponse(
        long userId,
        String userType,
        String fullName,
        String email,
        String status,
        String storeRole,
        Instant assignedAt,
        String assignedBy) {
}
