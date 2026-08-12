package com.renovar.canteiro.io.platform.company.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CompanyOnboardingCreateRequest(
        @NotBlank String corporateName,
        String tradeName,
        @NotBlank String document,
        @NotBlank @Email String email,
        String phone,
        String address,
        String logo,
        @NotBlank @Email String ownerEmail,
        @NotEmpty List<@NotNull UUID> planIds
) {
}
