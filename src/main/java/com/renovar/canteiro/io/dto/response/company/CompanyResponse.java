package com.renovar.canteiro.io.dto.response.company;

import java.time.LocalDateTime;
import java.util.UUID;

public record CompanyResponse(
    UUID id,
    String corporateName,
    String tradeName,
    String document,
    String email,
    String phone,
    String address,
    String logo,
    Boolean active,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
