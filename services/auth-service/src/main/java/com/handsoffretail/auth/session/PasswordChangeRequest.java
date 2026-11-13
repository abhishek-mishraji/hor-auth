package com.handsoffretail.auth.session;

import jakarta.validation.constraints.NotBlank;

public record PasswordChangeRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
}
