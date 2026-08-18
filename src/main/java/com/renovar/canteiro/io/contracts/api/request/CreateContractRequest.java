package com.renovar.canteiro.io.contracts.api.request;

import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateContractRequest(
        @NotNull UUID workId,
        String reference,
        @NotBlank String name,
        @NotNull ContractStatus status,
        LocalDate startedOn,
        LocalDate expectedCompletionOn,
        LocalDate completedOn,
        String justification
) {
}
