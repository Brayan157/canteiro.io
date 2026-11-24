package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.receivables.application.InvoiceSettlementService;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(requiredProperties = {"invoiceId", "invoiceNumber", "invoiceItemId", "billingAllocationId", "contractServiceId", "contractServiceName", "measurementContractCompositionId", "invoicedItemAmount", "itemReceivedAmount", "invoiceNetAmount", "invoiceReceivedAmount", "eligible", "maximumLineAmount", "unavailableReason", "withheldItemAmount", "netInvoicedItemAmount", "netAvailableItemAmount"},
        description = "Exact selectable origin; limits overlap and cannot be added. Invoice and title limits are revalidated jointly for all manual lines; Registered customer withholding is allocated proportionally by exact gross invoice item, with deterministic cents. Net amounts are unavailable when title scope or historical attribution cannot be proven. Partial cash remains manual.")
public record InvoiceSettlementOptionResponse(UUID invoiceId, String invoiceNumber, UUID invoiceItemId, UUID billingAllocationId,
        UUID contractServiceId, String contractServiceName, @Schema(types = {"string", "null"}, format = "uuid") UUID measurementContractCompositionId,
        @Schema(type = "string", format = "decimal", example = "100.00") String invoicedItemAmount,
        @Schema(type = "string", format = "decimal", example = "20.00") String itemReceivedAmount,
        @Schema(type = "string", format = "decimal", example = "90.00") String invoiceNetAmount,
        @Schema(type = "string", format = "decimal", example = "20.00") String invoiceReceivedAmount,
        boolean eligible, @Schema(types = {"string", "null"}, format = "decimal", example = "70.00") String maximumLineAmount,
        @Schema(types = {"string", "null"}) String unavailableReason,
        @Schema(types = {"string", "null"}, format = "decimal", pattern = "^[0-9]+\\.[0-9]{2}$", example = "10.00", description = "Proportional registered customer withholding for this exact item; null when unproven.") String withheldItemAmount,
        @Schema(types = {"string", "null"}, format = "decimal", pattern = "^[0-9]+\\.[0-9]{2}$", example = "90.00", description = "Gross invoiced item less its withholding share; cash is not included.") String netInvoicedItemAmount,
        @Schema(types = {"string", "null"}, format = "decimal", pattern = "^[0-9]+\\.[0-9]{2}$", example = "70.00", description = "Net item less signed confirmed exact cash; still subject to shared invoice/title limits.") String netAvailableItemAmount) {
    public static InvoiceSettlementOptionResponse from(InvoiceSettlementService.Option value) {
        var c = value.item();
        return new InvoiceSettlementOptionResponse(c.invoiceId(), c.invoiceNumber(), c.invoiceItemId(), c.billingAllocationId(), c.contractServiceId(),
                c.contractServiceName(), c.measurementContractCompositionId(), c.invoicedItemAmount().setScale(2).toPlainString(), c.itemReceivedAmount().setScale(2).toPlainString(),
                c.invoiceNetAmount().setScale(2).toPlainString(), c.invoiceReceivedAmount().setScale(2).toPlainString(), value.eligible(),
                value.maximumLineAmount() == null ? null : value.maximumLineAmount().setScale(2).toPlainString(), value.unavailableReason(), decimal(value.capacity().withheldAmount()), decimal(value.capacity().netAmount()), decimal(value.capacity().availableAmount()));
    }
    private static String decimal(java.math.BigDecimal value) { return value == null ? null : value.setScale(2).toPlainString(); }
}
