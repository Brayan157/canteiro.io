package com.renovar.canteiro.io.customers.api.response;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;

import java.util.UUID;

public record FinalCustomerContactChangeResponse(
        FinalCustomerContactResponse contact,
        UUID changeRequestId,
        ChangeAuthorizationMode mode
) {
}
