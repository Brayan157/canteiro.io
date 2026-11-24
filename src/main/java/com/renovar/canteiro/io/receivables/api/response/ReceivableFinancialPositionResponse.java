package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.receivables.domain.ReceivableFinancialPosition;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Official current title position, same currency as the title. Cash is confirmedSettlementAmount net of reversals; credits and withholding are separate. No invoice attribution is inferred. CANCELLED/RENEGOTIATED are historical, not amounts to collect.",
        requiredProperties = {"status", "withheldAmount", "creditedAmount", "netReceivableAmount", "confirmedSettlementAmount", "outstandingAmount"})
public record ReceivableFinancialPositionResponse(ReceivableFinancialPosition.Status status,
        @Schema(type = "string", format = "decimal", pattern = "^(0|[1-9][0-9]*)\\.[0-9]{2}$", example = "10.00") String withheldAmount,
        @Schema(type = "string", format = "decimal", pattern = "^(0|[1-9][0-9]*)\\.[0-9]{2}$", example = "0.00") String creditedAmount,
        @Schema(type = "string", format = "decimal", pattern = "^(0|[1-9][0-9]*)\\.[0-9]{2}$", example = "90.00") String netReceivableAmount,
        @Schema(type = "string", format = "decimal", pattern = "^(0|[1-9][0-9]*)\\.[0-9]{2}$", example = "40.00") String confirmedSettlementAmount,
        @Schema(type = "string", format = "decimal", pattern = "^(0|[1-9][0-9]*)\\.[0-9]{2}$", example = "50.00") String outstandingAmount) {
    public static ReceivableFinancialPositionResponse from(ReceivableFinancialPosition value) {
        return new ReceivableFinancialPositionResponse(value.status(), value.withheldAmount().toPlainString(),
                value.creditedAmount().toPlainString(), value.netReceivableAmount().toPlainString(),
                value.confirmedSettlementAmount().toPlainString(), value.outstandingAmount().toPlainString());
    }
}
