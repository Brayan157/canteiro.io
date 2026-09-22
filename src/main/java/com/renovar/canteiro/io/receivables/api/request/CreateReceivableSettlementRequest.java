package com.renovar.canteiro.io.receivables.api.request;

import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementPaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateReceivableSettlementRequest(
        @NotNull LocalDate receivedOn,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal amount,
        @NotNull ReceivableSettlementPaymentMethod paymentMethod,
        String note,
        String justification
) {
}
