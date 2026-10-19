package com.handsoff.hor.auth.admins;

import jakarta.validation.constraints.NotBlank;

public record PasswordResetRequest(@NotBlank String newPassword) {
}
