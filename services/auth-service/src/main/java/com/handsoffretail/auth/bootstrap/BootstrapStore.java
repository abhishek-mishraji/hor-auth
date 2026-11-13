package com.handsoffretail.auth.bootstrap;

public record BootstrapStore(
        String storeName,
        String storeCode,
        String posVendor,
        String s3Folder,
        String addressLine1,
        String city,
        String stateCode,
        String postalCode,
        String countryCode,
        String timezone) {
}
