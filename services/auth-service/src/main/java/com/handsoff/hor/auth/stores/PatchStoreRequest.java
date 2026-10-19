package com.handsoff.hor.auth.stores;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PatchStoreRequest(
        String storeCode,
        String storeName,
        String posVendor,
        String s3Folder,
        String addressLine1,
        String addressLine2,
        String city,
        @Size(max = 2) String stateCode,
        String postalCode,
        @Size(max = 2) String countryCode,
        @Pattern(regexp = "^$|^\\+?[0-9 ()-]{7,20}$") String contactNumber,
        String timezone,
        String status) {
}
