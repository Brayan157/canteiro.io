package com.renovar.canteiro.io.receivables.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record BillingServiceControlResponse(UUID contractServiceId, String serviceName,
                                            @Schema(type = "string", format = "decimal", example = "99.90", pattern = "^-?\\d+\\.\\d{2}$") String serviceNetAmount,
                                            @Schema(type = "string", format = "decimal", example = "99.90", pattern = "^-?\\d+\\.\\d{2}$") String activeAllocatedAmount,
                                            @Schema(type = "string", format = "decimal", example = "99.90", pattern = "^-?\\d+\\.\\d{2}$") String availableAmount,
                                            @Schema(type = "string", format = "decimal", example = "99.90", pattern = "^-?\\d+\\.\\d{2}$") String issuedInInvoicesAmount) {
}
