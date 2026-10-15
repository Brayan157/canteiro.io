package com.renovar.canteiro.io.contracts.api.response;

import com.renovar.canteiro.io.contracts.domain.ContractServiceStatus;
import com.renovar.canteiro.io.contracts.domain.DiscountType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record ContractServiceResponse(
        UUID id,
        UUID contractId,
        UUID sourceServiceTemplateId,
        String name,
        String description,
        ContractServiceStatus status,
        @Schema(type = "string", format = "decimal", example = "12.500") String quantity,
        @Schema(type = "string", format = "decimal", example = "150.00") String unitPrice,
        @Schema(type = "string", format = "decimal", example = "1875.00") String grossAmount,
        DiscountType discountType,
        @Schema(type = "string", format = "decimal", example = "10.0000") String discountValue,
        @Schema(type = "string", format = "decimal", example = "187.50") String discountAmount,
        @Schema(type = "string", format = "decimal", example = "1687.50") String netAmount,
        @Schema(type = "string", format = "decimal", example = "1200.50") String measurementCompositionAmount,
        @Schema(type = "string", format = "decimal", example = "2888.00") String effectiveNetAmount
) {
}
