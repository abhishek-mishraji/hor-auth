package com.handsoff.hor.auth.users;

import jakarta.validation.constraints.NotBlank;

public record UserPasswordResetRequest(@NotBlank String newPassword) {
}
