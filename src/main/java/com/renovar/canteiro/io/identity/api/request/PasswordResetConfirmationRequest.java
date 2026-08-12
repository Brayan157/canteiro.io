package com.renovar.canteiro.io.identity.api.request;

import jakarta.validation.constraints.NotBlank;

public record PasswordResetConfirmationRequest(
        @NotBlank String passwordResetToken,
        @NotBlank String newPassword
) {
}
