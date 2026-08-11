package com.renovar.canteiro.io.platform.company.api.response;

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
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
