package com.handsoffretail.auth.users;

import jakarta.validation.constraints.NotBlank;

public record UserPasswordResetRequest(@NotBlank String newPassword) {
}
