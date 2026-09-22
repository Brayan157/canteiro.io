package com.renovar.canteiro.io.platform.catalog.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateCatalogPriceRequest(
        @NotBlank
        @Pattern(regexp = "^(?:0|[1-9]\\d*)\\.\\d{2}$")
        @Schema(type = "string", format = "decimal", example = "99.90", pattern = "^(?:0|[1-9]\\d*)\\.\\d{2}$")
        String amount,
        @NotNull LocalDate validFrom,
        LocalDate validUntil
) {

    public BigDecimal amountAsBigDecimal() {
        return new BigDecimal(amount);
    }
}
