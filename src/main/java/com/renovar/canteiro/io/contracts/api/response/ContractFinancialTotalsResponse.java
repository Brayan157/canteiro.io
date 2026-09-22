package com.renovar.canteiro.io.contracts.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record ContractFinancialTotalsResponse(
        @Schema(type = "string", format = "decimal", example = "1750.00") String serviceSubtotal,
        @Schema(type = "string", format = "decimal", example = "0.00") String contractDiscountAmount,
        @Schema(type = "string", format = "decimal", example = "1750.00") String netAmount
) {
}
