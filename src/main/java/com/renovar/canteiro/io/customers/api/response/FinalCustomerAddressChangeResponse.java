package com.renovar.canteiro.io.customers.api.response;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;

import java.util.UUID;

public record FinalCustomerAddressChangeResponse(
        FinalCustomerAddressResponse address,
        UUID changeRequestId,
        ChangeAuthorizationMode mode
) {
}
