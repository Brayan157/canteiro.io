package com.renovar.canteiro.io.contracts.api.request;

import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;

import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import com.renovar.canteiro.io.contracts.domain.ContractPricingMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateContractRequest(
        @NotNull UUID workId,
        @jakarta.validation.constraints.Size(max = 100) String contractIdentifier,
        ContractPricingMode pricingMode,
        String reference,
        @NotBlank String name,
        @NotNull ContractStatus status,
        LocalDate startedOn,
        LocalDate expectedCompletionOn,
        LocalDate completedOn,
        String justification,
        @Schema(description = "Moeda ISO imutável. Padrão BRL; medição vinculada deriva a moeda do contrato quando omitida. Sem conversão.", example = "BRL") CurrencyCode currency
) {
}
