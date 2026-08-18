package com.renovar.canteiro.io.contracts.application;

import com.renovar.canteiro.io.contracts.domain.ContractStatus;

import java.time.LocalDate;
import java.util.UUID;

public record CreateContractCommand(
        UUID workId,
        String reference,
        String name,
        ContractStatus status,
        LocalDate startedOn,
        LocalDate expectedCompletionOn,
        LocalDate completedOn,
        String justification
) {
}
