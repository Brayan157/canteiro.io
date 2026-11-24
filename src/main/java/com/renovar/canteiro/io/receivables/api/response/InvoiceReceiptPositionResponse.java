package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.receivables.domain.InvoiceReceiptPosition;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Official receipt position, separate from documentary invoice.status. Exact manual invoice-item ledger determines cash, net of exact reversals; never infer cash from a shared contract/title. Requires RECEIVABLES.READ plus INVOICES.READ. Unknown legacy attribution, credits or renegotiation remain unavailable with null amounts. Existing invoice withholding affects only that invoice's net balance, without item distribution. SETTLED_BY_ADJUSTMENTS is not money received. The legacy generic receipt endpoint remains blocked.",
        requiredProperties = {"available", "status", "unavailableReason", "receivedAmount", "outstandingAmount", "calculatedAt"})
public record InvoiceReceiptPositionResponse(boolean available, InvoiceReceiptPosition.Status status,
        InvoiceReceiptPosition.Reason unavailableReason,
        @Schema(types = {"string", "null"}, format = "decimal", pattern = "^(0|[1-9][0-9]*)\\.[0-9]{2}$", description = "Exact confirmed cash net of linked reversals; null when unavailable.") String receivedAmount,
        @Schema(types = {"string", "null"}, format = "decimal", pattern = "^(0|[1-9][0-9]*)\\.[0-9]{2}$", description = "Official invoice net amount less exact cash, including its own withholding; null when unavailable.") String outstandingAmount,
        @Schema(description = "UTC instant at which availability was evaluated, not a payment timestamp.") Instant calculatedAt) {
    public static InvoiceReceiptPositionResponse from(InvoiceReceiptPosition value) {
        return new InvoiceReceiptPositionResponse(value.available(), value.status(), value.unavailableReason(),
                value.receivedAmount() == null ? null : value.receivedAmount().setScale(2).toPlainString(),
                value.outstandingAmount() == null ? null : value.outstandingAmount().setScale(2).toPlainString(), value.calculatedAt());
    }
}
