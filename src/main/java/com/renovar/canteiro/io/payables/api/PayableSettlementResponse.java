package com.renovar.canteiro.io.payables.api;

import com.renovar.canteiro.io.payables.domain.PayablePaymentMethod;
import com.renovar.canteiro.io.payables.domain.PayableSettlement;
import com.renovar.canteiro.io.shared.api.json.CanonicalMoneySerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PayableSettlementResponse(UUID id, UUID payableId,
        @Schema(types = {"string", "null"}, format = "uuid", description = "Original payment ID for a reversal.") UUID reversalOfId,
        @JsonSerialize(using = CanonicalMoneySerializer.class)
        @Schema(type = "string", format = "decimal", description = "Positive payment or negative reversal.", example = "-50.00") BigDecimal amount,
        LocalDate effectiveOn, PayablePaymentMethod paymentMethod, Instant recordedAt) {
    static PayableSettlementResponse from(PayableSettlement row) {
        return new PayableSettlementResponse(row.id(), row.payableId(), row.reversalOfId(), row.amount(),
                row.effectiveOn(), row.paymentMethod(), row.recordedAt());
    }
}
