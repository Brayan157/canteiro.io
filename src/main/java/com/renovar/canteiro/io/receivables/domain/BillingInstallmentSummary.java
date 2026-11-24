package com.renovar.canteiro.io.receivables.domain;

import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Historical installment amount, not an active allocation balance or an amount still receivable. */
public record BillingInstallmentSummary(UUID id, UUID contractId, String reference, LocalDate issuedOn, LocalDate dueOn,
        BillingInstallmentStatus status, CurrencyCode currency, BigDecimal netAmount, Instant createdAt, Instant updatedAt) {
}
