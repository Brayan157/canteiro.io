package com.renovar.canteiro.io.contracts.api.request;

import com.renovar.canteiro.io.contracts.domain.ContractServiceStatus;
import com.renovar.canteiro.io.contracts.domain.DiscountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpsertContractServiceRequest(
        @Schema(description = "Optional immutable source template. On update it must match the service's existing source template.") UUID sourceServiceTemplateId,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 1000) String description,
        @NotNull ContractServiceStatus status,
        @NotBlank @Pattern(regexp = "^(?:0|[1-9]\\d*)\\.\\d{1,4}$")
        @Schema(type = "string", format = "decimal", example = "12.500") String quantity,
        @NotBlank @Pattern(regexp = "^(?:0|[1-9]\\d*)\\.\\d{2}$")
        @Schema(type = "string", format = "decimal", example = "150.00") String unitPrice,
        DiscountType discountType,
        @Pattern(regexp = "^(?:0|[1-9]\\d*)\\.\\d{1,4}$")
        @Schema(type = "string", format = "decimal", example = "10.0000") String discountValue,
        @Size(max = 1000) String justification
) {
}
