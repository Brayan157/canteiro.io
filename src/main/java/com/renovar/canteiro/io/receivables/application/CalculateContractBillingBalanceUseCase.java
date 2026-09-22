package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.contracts.application.CalculateContractNetAmountUseCase;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationRepository;
import com.renovar.canteiro.io.receivables.domain.ContractBillingBalance;
import com.renovar.canteiro.io.receivables.domain.ContractBillingBalanceCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CalculateContractBillingBalanceUseCase {

    private final CalculateContractNetAmountUseCase calculateContractNetAmountUseCase;
    private final BillingAllocationRepository billingAllocationRepository;

    @Transactional(readOnly = true)
    public ContractBillingBalance calculate(UUID companyId, UUID contractId) {
        return ContractBillingBalanceCalculator.calculate(
                calculateContractNetAmountUseCase.calculate(companyId, contractId),
                billingAllocationRepository.findByContractIdAndCompanyId(contractId, companyId)
        );
    }
}
