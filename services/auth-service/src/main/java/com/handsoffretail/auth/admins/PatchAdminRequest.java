package com.handsoffretail.auth.admins;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record PatchAdminRequest(
        @Email @Size(max = 254) String email,
        @Size(max = 200) String fullName,
        @Pattern(regexp = "^$|^\\+?[0-9 ()-]{7,20}$") String phoneNumber,
        String status) {
}
