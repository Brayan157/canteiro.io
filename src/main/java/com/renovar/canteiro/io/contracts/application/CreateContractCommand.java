package com.renovar.canteiro.io.contracts.application;

import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import com.renovar.canteiro.io.contracts.domain.ContractPricingMode;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;

import java.time.LocalDate;
import java.util.UUID;

public record CreateContractCommand(
        UUID workId,
        String contractIdentifier,
        ContractPricingMode pricingMode,
        String reference,
        String name,
        ContractStatus status,
        LocalDate startedOn,
        LocalDate expectedCompletionOn,
        LocalDate completedOn,
        String justification,
        CurrencyCode currency
) {
    public CreateContractCommand(UUID workId, String contractIdentifier, ContractPricingMode pricingMode, String reference,
                                 String name, ContractStatus status, LocalDate startedOn, LocalDate expectedCompletionOn,
                                 LocalDate completedOn, String justification) {
        this(workId, contractIdentifier, pricingMode, reference, name, status, startedOn, expectedCompletionOn, completedOn, justification, null);
    }
}
