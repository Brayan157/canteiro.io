package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.receivables.application.InvoiceSettlementService;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "Read-only proposal for the entire outstanding title balance using its exact documented billing origins. Uses exact remaining net item capacities after proportional customer withholding within one original title scope; deterministic cents, never FIFO or automatic partial cash distribution. Unknown scope or incompatible historical cash requires reconciliation. Independent of the options page. No reservation; POST and approval revalidate. Partial receipts remain manual.",
        requiredProperties = {"available", "unavailableReason", "amount", "items"})
public record TotalInvoiceSettlementProposalResponse(boolean available,
        @Schema(types = {"string", "null"}) String unavailableReason,
        @Schema(types = {"string", "null"}, format = "decimal", pattern = "^[0-9]+\\.[0-9]{2}$", example = "100.00") String amount,
        List<Item> items) {
    public static TotalInvoiceSettlementProposalResponse from(InvoiceSettlementService.TotalProposal value) {
        return new TotalInvoiceSettlementProposalResponse(value.available(), value.unavailableReason(),
                value.amount() == null ? null : value.amount().setScale(2).toPlainString(), value.items().stream().map(line ->
                new Item(line.invoiceId(), line.invoiceItemId(), line.billingAllocationId(), line.measurementContractCompositionId(), line.amount().setScale(2).toPlainString())).toList());
    }

    @Schema(name = "TotalInvoiceSettlementProposalItem", requiredProperties = {"invoiceId", "invoiceItemId", "billingAllocationId", "measurementContractCompositionId", "amount"})
    public record Item(UUID invoiceId, UUID invoiceItemId, UUID billingAllocationId,
            @Schema(types = {"string", "null"}, format = "uuid") UUID measurementContractCompositionId,
            @Schema(type = "string", format = "decimal", pattern = "^[0-9]+\\.[0-9]{2}$", example = "60.00") String amount) { }
}
