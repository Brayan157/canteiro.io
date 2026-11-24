package com.renovar.canteiro.io.receivables.api.request;

import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementPaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateReceivableSettlementRequest(
        @io.swagger.v3.oas.annotations.media.Schema(description = "Actual receipt business date, ISO yyyy-MM-dd. Must not exceed the current UTC calendar date from the official server Clock; not a scheduled payment or due date. Revalidated on direct application and approval. GET receivables/{receivableId}/balance returns maxReceivedOn.", type = "string", format = "date")
        @NotNull LocalDate receivedOn,
        @io.swagger.v3.oas.annotations.media.Schema(type = "string", format = "decimal", example = "100.00", pattern = "^-?(0|[1-9][0-9]*)\\.[0-9]{2}$")
        @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal amount,
        @NotNull ReceivableSettlementPaymentMethod paymentMethod,
        String note,
        String justification
) {
}
