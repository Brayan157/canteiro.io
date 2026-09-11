package com.renovar.canteiro.io.platform.catalog.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CatalogPriceResponse(
        UUID id,
        UUID catalogItemId,
        @Schema(type = "string", format = "decimal", example = "99.90", pattern = "^-?\\d+\\.\\d{2}$")
        String amount,
        LocalDate validFrom,
        LocalDate validUntil,
        Instant createdAt,
        Instant updatedAt
) {
}
