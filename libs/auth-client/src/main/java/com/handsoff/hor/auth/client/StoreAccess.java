package com.handsoff.hor.auth.client;

public final class StoreAccess {
    private StoreAccess() {
    }

    public static void requireStore(HorPrincipal principal, long storeId) {
        if (!principal.allStores() && !principal.storeIds().contains(storeId)) {
            throw new DomainException(CommonErrorCode.NOT_FOUND, "Store not found.");
        }
    }
}
