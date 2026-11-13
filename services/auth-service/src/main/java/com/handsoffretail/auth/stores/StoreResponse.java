package com.handsoffretail.auth.stores;

import java.time.Instant;

public record StoreResponse(
        long id,
        String storeCode,
        String storeName,
        String posVendor,
        String s3Folder,
        String addressLine1,
        String addressLine2,
        String city,
        String stateCode,
        String postalCode,
        String countryCode,
        String contactNumber,
        String timezone,
        String status,
        String ownerName,
        long memberCount,
        Instant createdAt,
        Instant updatedAt) {
}
