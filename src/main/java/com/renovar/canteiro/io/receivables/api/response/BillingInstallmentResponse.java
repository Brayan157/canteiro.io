package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.receivables.domain.BillingInstallmentStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record BillingInstallmentResponse(
        UUID id,
        UUID contractId,
        String reference,
        String description,
        LocalDate issuedOn,
        LocalDate dueOn,
        BillingInstallmentStatus status,
        List<BillingAllocationResponse> allocations,
        com.renovar.canteiro.io.shared.domain.CurrencyCode currency
) {
}
