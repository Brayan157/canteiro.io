package com.renovar.canteiro.io.receivables.domain;

import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InvoiceSummary(UUID id, UUID contractId, String contractName, String contractReference,
                             String contractIdentifier, String number, String series, LocalDate issuedOn,
                             InvoiceStatus status, CurrencyCode currency, BigDecimal totalAmount,
                             Instant createdAt, Instant updatedAt) {
}
