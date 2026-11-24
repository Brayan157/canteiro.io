package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.receivables.application.InvoiceSettlementService;
import com.renovar.canteiro.io.shared.api.pagination.PageResponse;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

@Schema(requiredProperties = {"receivableId", "contractId", "currency", "eligible", "unavailableReason", "maxReceivedOn", "outstandingAmount", "items", "totalSettlementProposal"})
public record InvoiceSettlementOptionsResponse(UUID receivableId, UUID contractId, CurrencyCode currency, boolean eligible,
        @Schema(types = {"string", "null"}) String unavailableReason, LocalDate maxReceivedOn,
        @Schema(type = "string", format = "decimal", example = "90.00") String outstandingAmount,
        PageResponse<InvoiceSettlementOptionResponse> items, TotalInvoiceSettlementProposalResponse totalSettlementProposal) {
    public static InvoiceSettlementOptionsResponse from(InvoiceSettlementService.Options value) {
        return new InvoiceSettlementOptionsResponse(value.receivableId(), value.contractId(), value.currency(), value.eligible(), value.unavailableReason(),
                value.maxReceivedOn(), value.balance().outstandingAmount().setScale(2).toPlainString(), PageResponse.from(value.items(), InvoiceSettlementOptionResponse::from),
                TotalInvoiceSettlementProposalResponse.from(value.totalSettlementProposal()));
    }
}
