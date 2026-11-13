package com.handsoffretail.auth.admins;

import jakarta.validation.constraints.NotBlank;

public record PasswordResetRequest(@NotBlank String newPassword) {
}
