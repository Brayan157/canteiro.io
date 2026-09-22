package com.renovar.canteiro.io.contracts.api.request;

import com.renovar.canteiro.io.contracts.domain.DiscountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SetContractDiscountRequest(
        @NotNull DiscountType discountType,
        @NotBlank @Pattern(regexp = "^(?:0|[1-9]\\d*)\\.\\d{1,4}$")
        @Schema(type = "string", format = "decimal", example = "10.0000") String discountValue,
        @NotBlank @Size(max = 1000) String justification
) {
}
