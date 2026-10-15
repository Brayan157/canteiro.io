package com.renovar.canteiro.io.contracts.application;

import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;

import java.time.LocalDate;

public record UpdateContractCommand(
        String contractIdentifier,
        String reference,
        String name,
        ContractStatus status,
        LocalDate startedOn,
        LocalDate expectedCompletionOn,
        LocalDate completedOn,
        String justification,
        CurrencyCode currency
) {
    public UpdateContractCommand(String contractIdentifier, String reference, String name, ContractStatus status,
                                 LocalDate startedOn, LocalDate expectedCompletionOn, LocalDate completedOn, String justification) {
        this(contractIdentifier, reference, name, status, startedOn, expectedCompletionOn, completedOn, justification, null);
    }
}
