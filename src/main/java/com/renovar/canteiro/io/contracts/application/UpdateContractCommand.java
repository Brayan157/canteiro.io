package com.renovar.canteiro.io.contracts.application;

import com.renovar.canteiro.io.contracts.domain.ContractStatus;

import java.time.LocalDate;

public record UpdateContractCommand(
        String contractIdentifier,
        String reference,
        String name,
        ContractStatus status,
        LocalDate startedOn,
        LocalDate expectedCompletionOn,
        LocalDate completedOn,
        String justification
) {
}
