package com.renovar.canteiro.io.customers.api.response;

import java.util.UUID;

public record FinalCustomerAddressResponse(
        UUID id,
        String label,
        String postalCode,
        String street,
        String number,
        String complement,
        String district,
        String city,
        String state,
        String country,
        boolean primaryAddress,
        boolean active
) {
}
