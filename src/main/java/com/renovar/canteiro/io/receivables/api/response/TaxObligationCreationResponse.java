package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;

import java.util.UUID;

public record TaxObligationCreationResponse(
        TaxObligationResponse taxObligation,
        UUID payableId,
        UUID changeRequestId,
        ChangeAuthorizationMode mode
) {
}
