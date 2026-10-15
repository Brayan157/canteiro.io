package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementPaymentMethod;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementStatus;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementEntryType;
import com.renovar.canteiro.io.shared.api.json.CanonicalMoneySerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ReceivableSettlementResponse(
        UUID id,
        UUID receivableId,
        UUID contractId,
        LocalDate receivedOn,
        @JsonSerialize(using = CanonicalMoneySerializer.class)
        @Schema(type = "string", format = "decimal", pattern = "^-?(0|[1-9][0-9]*)\\.[0-9]{2}$", example = "100.00") BigDecimal amount,
        ReceivableSettlementEntryType entryType,
        UUID reversalOfSettlementId,
        ReceivableSettlementPaymentMethod paymentMethod,
        String note,
        ReceivableSettlementStatus status,
        com.renovar.canteiro.io.shared.domain.CurrencyCode currency
) {
}
