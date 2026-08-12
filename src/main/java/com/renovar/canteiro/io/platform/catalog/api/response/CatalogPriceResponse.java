package com.renovar.canteiro.io.platform.catalog.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CatalogPriceResponse(
        UUID id,
        UUID catalogItemId,
        BigDecimal amount,
        LocalDate validFrom,
        LocalDate validUntil,
        Instant createdAt,
        Instant updatedAt
) {
}
