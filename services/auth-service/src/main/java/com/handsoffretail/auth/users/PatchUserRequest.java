package com.handsoffretail.auth.users;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PatchUserRequest(
        String userType,
        @Email @Size(max = 254) String email,
        @Size(max = 200) String fullName,
        @Pattern(regexp = "^$|^\\+?[0-9 ()-]{7,20}$") String phoneNumber,
        @Size(max = 500) String address,
        String status) {
}
