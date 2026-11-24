package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.receivables.domain.InvoiceSettlementRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(requiredProperties = {"id", "settlementId", "invoiceId", "invoiceItemId", "billingAllocationId", "measurementContractCompositionId", "amount", "reversalOfLineId", "createdAt"})
public record InvoiceSettlementLineResponse(UUID id, UUID settlementId, UUID invoiceId, UUID invoiceItemId, UUID billingAllocationId,
        @Schema(types = {"string", "null"}, format = "uuid") UUID measurementContractCompositionId,
        @Schema(type = "string", format = "decimal", example = "20.00") String amount,
        @Schema(types = {"string", "null"}, format = "uuid") UUID reversalOfLineId, Instant createdAt) {
    public static InvoiceSettlementLineResponse from(InvoiceSettlementRepository.Line value) {
        return new InvoiceSettlementLineResponse(value.id(), value.settlementId(), value.invoiceId(), value.invoiceItemId(), value.billingAllocationId(),
                value.measurementContractCompositionId(), value.amount().setScale(2).toPlainString(), value.reversalOfLineId(), value.createdAt());
    }
}
