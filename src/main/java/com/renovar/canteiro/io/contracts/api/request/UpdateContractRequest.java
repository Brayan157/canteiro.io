package com.renovar.canteiro.io.contracts.api.request;

import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;

import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateContractRequest(
        @Size(max = 100) String contractIdentifier,
        @Schema(description = "Optional contract number, unique per work regardless of case and surrounding whitespace. Keeping the same number permits edits to legacy duplicates.", example = "CTR/A-001.2026")
        @Size(max = 100) String reference,
        @NotBlank @Size(max = 255) String name,
        @NotNull ContractStatus status,
        LocalDate startedOn,
        LocalDate expectedCompletionOn,
        LocalDate completedOn,
        @Size(max = 1000) String justification,
        @Schema(description = "Opcional; somente a moeda original é aceita. Moeda é imutável, inclusive em rascunho.", example = "BRL") CurrencyCode currency
) {
}
