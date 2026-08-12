package com.renovar.canteiro.io.platform.support.api.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePlatformSupportUserRequest(
        @NotBlank @Email @Size(max = 255) String email
) {
}
