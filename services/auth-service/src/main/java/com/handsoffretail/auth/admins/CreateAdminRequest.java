package com.handsoffretail.auth.admins;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAdminRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 200) String fullName,
        @Pattern(regexp = "^$|^\\+?[0-9 ()-]{7,20}$") String phoneNumber,
        @NotBlank String password) {
}
