package com.handsoffretail.auth.users;

public record UserStoreView(long storeId, String storeCode, String storeName, String storeRole, String storeStatus) {
}
