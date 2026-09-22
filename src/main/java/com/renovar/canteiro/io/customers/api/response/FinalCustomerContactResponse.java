package com.renovar.canteiro.io.customers.api.response;

import java.util.UUID;

public record FinalCustomerContactResponse(
        UUID id,
        String name,
        String email,
        String phone,
        boolean primaryContact,
        boolean active
) {
}
