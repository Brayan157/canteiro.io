package com.renovar.canteiro.io.receivables.api.request;

import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementPaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateReceivableSettlementRequest(
        @NotNull LocalDate receivedOn,
        @io.swagger.v3.oas.annotations.media.Schema(type = "string", format = "decimal", example = "100.00", pattern = "^-?(0|[1-9][0-9]*)\\.[0-9]{2}$")
        @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal amount,
        @NotNull ReceivableSettlementPaymentMethod paymentMethod,
        String note,
        String justification
) {
}
