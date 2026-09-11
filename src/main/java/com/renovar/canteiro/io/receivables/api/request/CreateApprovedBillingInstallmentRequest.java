package com.renovar.canteiro.io.receivables.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateApprovedBillingInstallmentRequest(
        @NotNull UUID contractId,
        String reference,
        String description,
        LocalDate issuedOn,
        @NotNull LocalDate dueOn,
        UUID financialGroupId,
        @NotEmpty List<@Valid BillingAllocationRequest> allocations,
        String justification
) {
}
