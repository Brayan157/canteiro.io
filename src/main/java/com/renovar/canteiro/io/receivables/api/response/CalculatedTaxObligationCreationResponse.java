package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
@Schema(requiredProperties = {"mode"}, description = "201 applied, 200 same-operation replay; 202 contains only changeRequestId and mode. Missing entities are omitted, never fictional objects. Original endpoints remain compatible.")
public record CalculatedTaxObligationCreationResponse(TaxObligationResponse taxObligation,
        InvoiceTaxCalculationResponse calculation, UUID payableId, UUID changeRequestId, ChangeAuthorizationMode mode) { }
