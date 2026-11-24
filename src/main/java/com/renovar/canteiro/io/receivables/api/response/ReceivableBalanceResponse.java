package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.receivables.domain.ReceivableStatus;
import com.renovar.canteiro.io.shared.api.json.CanonicalMoneySerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;

import java.math.BigDecimal;
import java.util.UUID;

public record ReceivableBalanceResponse(
        UUID receivableId,
        @JsonSerialize(using = CanonicalMoneySerializer.class)
        @Schema(type = "string", format = "decimal", pattern = "^-?(0|[1-9][0-9]*)\\.[0-9]{2}$", example = "100.00") BigDecimal originalAmount,
        @JsonSerialize(using = CanonicalMoneySerializer.class)
        @Schema(type = "string", format = "decimal", pattern = "^-?(0|[1-9][0-9]*)\\.[0-9]{2}$", example = "10.00") BigDecimal withheldAmount,
        @JsonSerialize(using = CanonicalMoneySerializer.class)
        @Schema(type = "string", format = "decimal", pattern = "^-?(0|[1-9][0-9]*)\\.[0-9]{2}$", example = "0.00") BigDecimal creditedAmount,
        @JsonSerialize(using = CanonicalMoneySerializer.class)
        @Schema(type = "string", format = "decimal", pattern = "^-?(0|[1-9][0-9]*)\\.[0-9]{2}$", example = "90.00") BigDecimal netReceivableAmount,
        @JsonSerialize(using = CanonicalMoneySerializer.class)
        @Schema(type = "string", format = "decimal", pattern = "^-?(0|[1-9][0-9]*)\\.[0-9]{2}$", example = "40.00") BigDecimal confirmedSettlementAmount,
        @JsonSerialize(using = CanonicalMoneySerializer.class)
        @Schema(type = "string", format = "decimal", pattern = "^-?(0|[1-9][0-9]*)\\.[0-9]{2}$", example = "50.00") BigDecimal outstandingAmount,
        ReceivableStatus status,
        com.renovar.canteiro.io.shared.domain.CurrencyCode currency,
        @Schema(type = "string", format = "date", requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Maximum receivedOn at response time: current UTC calendar date from the official server Clock. Keep yyyy-MM-dd unchanged, without browser timezone conversion. POST and approval revalidate independently; this is not settlement eligibility or an amount reservation.")
        java.time.LocalDate maxReceivedOn,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "False for the legacy generic /settlements command, which remains blocked. The explicit manual invoice-item flow uses /invoice-settlement-options and /invoice-settlements instead; this flag must never enable the legacy form. Historical reads and reversals remain available; a positive balance alone does not authorize a receipt.")
        boolean settlementCreationAvailable,
        @Schema(types = {"string", "null"}, requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Controlled reason for temporary receipt creation unavailability. Currently SETTLEMENT_INVOICE_LINK_REQUIRED. No invoice data, tax payload or free-form client content.")
        String settlementCreationBlockReason
) {
}
