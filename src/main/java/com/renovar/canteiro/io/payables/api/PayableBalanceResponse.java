package com.renovar.canteiro.io.payables.api;

import com.renovar.canteiro.io.payables.application.PayablePosition;
import com.renovar.canteiro.io.payables.domain.PayableStatus;
import com.renovar.canteiro.io.shared.api.json.CanonicalMoneySerializer;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Official position of one title, as of the UTC business date. Amounts are null when the available ledger cannot substantiate the title status.")
public record PayableBalanceResponse(UUID payableId, CurrencyCode currency, PayableStatus status, LocalDate asOf,
        @JsonSerialize(using = CanonicalMoneySerializer.class)
        @Schema(type = "string", format = "decimal", example = "150.00") BigDecimal originalAmount,
        boolean available,
        @JsonSerialize(using = CanonicalMoneySerializer.class)
        @Schema(types = {"string", "null"}, format = "decimal", example = "50.00") BigDecimal paidAmount,
        @JsonSerialize(using = CanonicalMoneySerializer.class)
        @Schema(types = {"string", "null"}, format = "decimal", example = "100.00") BigDecimal outstandingAmount,
        @JsonSerialize(using = CanonicalMoneySerializer.class)
        @Schema(types = {"string", "null"}, format = "decimal", example = "100.00") BigDecimal overdueAmount) {
    static PayableBalanceResponse from(PayablePosition position) {
        var title = position.payable();
        return new PayableBalanceResponse(title.getId(), title.getCurrency(), title.getStatus(), position.asOf(),
                title.getOriginalAmount(), position.available(), position.paidAmount(), position.outstandingAmount(), position.overdueAmount());
    }
}
