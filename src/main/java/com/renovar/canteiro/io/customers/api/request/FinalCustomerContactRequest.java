package com.renovar.canteiro.io.customers.api.request;

import jakarta.validation.constraints.NotBlank;

public record FinalCustomerContactRequest(
        @NotBlank String name,
        String email,
        String phone,
        boolean primaryContact,
        String justification
) {
}
