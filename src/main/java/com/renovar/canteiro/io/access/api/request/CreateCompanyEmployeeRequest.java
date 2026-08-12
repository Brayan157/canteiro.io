package com.renovar.canteiro.io.access.api.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCompanyEmployeeRequest(
        @NotBlank @Email @Size(max = 255) String email
) {
}
