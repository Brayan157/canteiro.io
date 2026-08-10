package com.renovar.canteiro.io.dto.request.company;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CompanyUpdateRequest (
    UUID id,
    String corporateName,
    String tradeName,
    String document,
    String email,
    String phone,
    String address,
    String logo
) {}
