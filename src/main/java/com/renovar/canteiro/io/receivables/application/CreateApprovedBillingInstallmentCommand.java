package com.renovar.canteiro.io.receivables.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateApprovedBillingInstallmentCommand(
        UUID contractId,
        String reference,
        String description,
        LocalDate issuedOn,
        LocalDate dueOn,
        UUID financialGroupId,
        List<BillingAllocationCommand> allocations,
        String justification
) {
}
