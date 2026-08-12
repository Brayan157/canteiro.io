package com.renovar.canteiro.io.identity.api.request;

import jakarta.validation.constraints.NotBlank;

public record ActivateAccountRequest(
        @NotBlank String activationToken,
        @NotBlank String password
) {
}
