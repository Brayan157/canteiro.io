package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.receivables.domain.ReceivableReadProjection;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Official title/context with the same financialPosition as the list. No individual settlements, tax payloads or invoice attribution. Settlement eligibility remains in the existing balance endpoint.", requiredProperties = {"receivable"})
public record ReceivableDetailsResponse(ReceivableSummaryResponse receivable,
        @Schema(types = {"string", "null"}) String description) {
    public static ReceivableDetailsResponse from(ReceivableReadProjection value) {
        return new ReceivableDetailsResponse(ReceivableSummaryResponse.from(value), value.description());
    }
}
