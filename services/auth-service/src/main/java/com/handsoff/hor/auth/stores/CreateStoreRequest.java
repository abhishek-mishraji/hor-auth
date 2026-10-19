package com.handsoff.hor.auth.stores;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateStoreRequest(
        @NotBlank @Size(max = 40) String storeCode,
        @NotBlank @Size(max = 200) String storeName,
        @NotBlank String posVendor,
        @NotBlank String s3Folder,
        @NotBlank String addressLine1,
        String addressLine2,
        @NotBlank String city,
        @NotBlank @Size(max = 2) String stateCode,
        @NotBlank String postalCode,
        @Size(max = 2) String countryCode,
        @Pattern(regexp = "^$|^\\+?[0-9 ()-]{7,20}$") String contactNumber,
        @NotBlank String timezone) {
}
