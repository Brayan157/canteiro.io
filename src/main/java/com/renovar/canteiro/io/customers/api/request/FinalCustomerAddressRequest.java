package com.renovar.canteiro.io.customers.api.request;

import jakarta.validation.constraints.NotBlank;

public record FinalCustomerAddressRequest(
        String label,
        String postalCode,
        @NotBlank String street,
        String number,
        String complement,
        String district,
        @NotBlank String city,
        @NotBlank String state,
        String country,
        boolean primaryAddress,
        String justification
) {
}
