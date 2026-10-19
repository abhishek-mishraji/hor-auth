package com.handsoff.hor.auth.users;

public record UserStoreView(long storeId, String storeCode, String storeName, String storeRole, String storeStatus) {
}
