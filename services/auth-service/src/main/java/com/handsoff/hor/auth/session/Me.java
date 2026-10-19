package com.handsoff.hor.auth.session;

import java.time.Instant;
import java.util.List;

public record Me(
        String accountType,
        long id,
        String email,
        String fullName,
        String role,
        boolean allStores,
        List<StoreMe> stores,
        Instant passwordChangedAt,
        Instant lastLoginAt) {
    public Me {
        stores = List.copyOf(stores);
    }
}
