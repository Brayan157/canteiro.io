package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(requiredProperties = {"settlement", "items", "currency", "confirmedSettlementAmount", "outstandingAmount", "mode", "replayed"})
public record InvoiceSettlementResponse(ReceivableSettlementResponse settlement, List<InvoiceSettlementLineResponse> items, CurrencyCode currency,
        @Schema(type = "string", format = "decimal", example = "20.00") String confirmedSettlementAmount,
        @Schema(type = "string", format = "decimal", example = "70.00") String outstandingAmount,
        ChangeAuthorizationMode mode, boolean replayed) {
    @Schema(name = "PendingInvoiceSettlementResponse", requiredProperties = {"changeRequestId", "mode", "replayed"}, description = "Only a proposal; no receipt, invoice line, reservation or official balance is created.")
    public record Pending(UUID changeRequestId, ChangeAuthorizationMode mode, boolean replayed) { }
}
