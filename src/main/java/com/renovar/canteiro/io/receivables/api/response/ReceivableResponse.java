package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.receivables.domain.ReceivableStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ReceivableResponse(
        UUID id,
        UUID contractId,
        UUID billingInstallmentId,
        UUID financialGroupId,
        String reference,
        LocalDate issuedOn,
        LocalDate dueOn,
        BigDecimal originalAmount,
        ReceivableStatus status
) {
}
