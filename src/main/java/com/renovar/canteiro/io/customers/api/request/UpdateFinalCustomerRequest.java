package com.renovar.canteiro.io.customers.api.request;

import com.renovar.canteiro.io.customers.domain.FinalCustomerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateFinalCustomerRequest(
        @NotNull FinalCustomerType customerType,
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 20) String document,
        @Size(max = 1000) String justification
) {
}
