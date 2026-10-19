package com.handsoff.hor.auth.config;

import jakarta.validation.Valid;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("hor.auth.bootstrap")
public record BootstrapProperties(@Valid Admin admin, @Valid DefaultStore defaultStore) {
    public record Admin(String email, String password, String fullName) {
    }

    public record DefaultStore(
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
}
