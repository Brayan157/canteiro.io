package com.renovar.canteiro.io.receivables.domain;

import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReceivableReadProjection(UUID id, String reference, String description,
        UUID contractId, String contractName, String contractReference, String contractIdentifier,
        UUID workId, String workName, String workReference, UUID finalCustomerId, String finalCustomerName,
        UUID billingInstallmentId, String billingInstallmentReference, UUID financialGroupId,
        UUID renegotiationSourceReceivableId, String renegotiationSourceReference,
        LocalDate issuedOn, LocalDate dueOn, ReceivableStatus status, CurrencyCode currency,
        BigDecimal originalAmount, Instant createdAt, Instant updatedAt,
        ReceivableFinancialPosition financialPosition) { }
