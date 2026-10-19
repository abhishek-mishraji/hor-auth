package com.handsoff.hor.auth.users;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Pattern(regexp = "STAFF|CLIENT") String userType,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 200) String fullName,
        @Pattern(regexp = "^$|^\\+?[0-9 ()-]{7,20}$") String phoneNumber,
        @Size(max = 500) String address,
        @NotBlank String password) {
}
