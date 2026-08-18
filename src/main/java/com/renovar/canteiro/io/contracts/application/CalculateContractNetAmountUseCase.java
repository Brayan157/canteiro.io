package com.renovar.canteiro.io.contracts.application;

import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.contracts.domain.ContractDiscountRepository;
import com.renovar.canteiro.io.contracts.domain.ContractNetAmount;
import com.renovar.canteiro.io.contracts.domain.ContractNetAmountCalculator;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.contracts.domain.ContractServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CalculateContractNetAmountUseCase {

    private final ContractRepository contractRepository;
    private final ContractServiceRepository contractServiceRepository;
    private final ContractDiscountRepository contractDiscountRepository;

    @Transactional(readOnly = true)
    public ContractNetAmount calculate(UUID companyId, UUID contractId) {
        contractRepository.findByIdAndCompanyId(contractId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Contract"));
        return ContractNetAmountCalculator.calculate(
                contractServiceRepository.findByContractIdAndCompanyId(contractId, companyId),
                contractDiscountRepository.findByContractIdAndCompanyId(contractId, companyId).orElse(null)
        );
    }
}
