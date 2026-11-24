package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.receivables.domain.InvoiceContractSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Minimal contract group represented by at least one official persisted invoice, including cancelled history. Current commercial labels, not fiscal snapshots. No amounts, balances, invoice counts or permission to read the full contract.",
        requiredProperties = {"contractId", "contractName", "contractReference", "contractIdentifier"})
public record InvoiceContractResponse(
        @Schema(description = "Stable internal navigation context. Select from this index; never match groups only by name.") UUID contractId,
        String contractName,
        @Schema(types = {"string", "null"}) String contractReference,
        @Schema(types = {"string", "null"}) String contractIdentifier) {
    public static InvoiceContractResponse from(InvoiceContractSummary value) {
        return new InvoiceContractResponse(value.contractId(), value.contractName(), value.contractReference(), value.contractIdentifier());
    }
}
