package com.renovar.canteiro.io.contracts.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Justification for an auditable reversal of an active discount. */
public record ReverseDiscountRequest(
        @NotBlank @Size(max = 1000) String justification
) {
}
