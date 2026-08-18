package com.renovar.canteiro.io.contracts.api.response;

import com.renovar.canteiro.io.contracts.domain.ContractStatus;

import java.time.LocalDate;
import java.util.UUID;

public record ContractResponse(
        UUID id,
        UUID workId,
        String reference,
        String name,
        ContractStatus status,
        LocalDate startedOn,
        LocalDate expectedCompletionOn,
        LocalDate completedOn
) {
}
