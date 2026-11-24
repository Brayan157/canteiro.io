package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.receivables.application.ReadInvoicesUseCase;
import com.renovar.canteiro.io.receivables.domain.InvoiceItemStatus;
import com.renovar.canteiro.io.shared.api.json.CanonicalMoneySerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Official invoice, safe receipt availability and all historical items. Exact invoice receipt attribution is currently unavailable, never inferred from allocation or contract cash. No measurement content, fiscal key, attachments or tax payload.",
        requiredProperties = {"invoice", "items"})
public record InvoiceDetailsResponse(InvoiceSummaryResponse invoice, List<ItemResponse> items) {
    public static InvoiceDetailsResponse from(ReadInvoicesUseCase.Details details) {
        return new InvoiceDetailsResponse(InvoiceSummaryResponse.from(details.invoice(), details.receiptPosition()), details.items().stream().map(line -> {
            var item = line.item();
            return new ItemResponse(item.getId(), item.getBillingAllocationId(), line.billingInstallmentId(), item.getContractServiceId(),
                    line.contractServiceName(), line.measurementContractCompositionId(), item.getDescription(), item.getAllocatedAmount(),
                    item.getStatus(), item.getCreatedAt(), item.getUpdatedAt());
        }).toList());
    }

    @Schema(name = "InvoiceReadItemResponse", requiredProperties = {"id", "billingAllocationId", "billingInstallmentId", "allocatedAmount", "status", "createdAt", "updatedAt"})
    public record ItemResponse(UUID id, UUID billingAllocationId, UUID billingInstallmentId,
            @Schema(types = {"string", "null"}, format = "uuid") UUID contractServiceId,
            @Schema(types = {"string", "null"}) String contractServiceName,
            @Schema(types = {"string", "null"}, format = "uuid", description = "Exact persisted measurement composition origin; null for negotiated base or legacy contract discount. No measurement content is disclosed.") UUID measurementContractCompositionId,
            @Schema(types = {"string", "null"}) String description,
            @JsonSerialize(using = CanonicalMoneySerializer.class)
            @Schema(type = "string", format = "decimal", pattern = "^-?(0|[1-9][0-9]*)\\.[0-9]{2}$", example = "99.90") BigDecimal allocatedAmount,
            InvoiceItemStatus status, Instant createdAt, Instant updatedAt) { }
}
