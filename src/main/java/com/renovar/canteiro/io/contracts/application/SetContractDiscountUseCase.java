package com.renovar.canteiro.io.contracts.application;

import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.contracts.domain.ContractDiscount;
import com.renovar.canteiro.io.contracts.domain.ContractDiscountRepository;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.contracts.domain.ContractServiceRepository;
import com.renovar.canteiro.io.contracts.domain.ContractNetAmountCalculator;
import com.renovar.canteiro.io.contracts.domain.DiscountType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SetContractDiscountUseCase {

    private final ContractRepository contractRepository;
    private final ContractDiscountRepository contractDiscountRepository;
    private final ContractServiceRepository contractServiceRepository;
    private final ApprovedContractBillingAmountProvider approvedContractBillingAmountProvider;

    @Transactional
    public ContractDiscount set(UUID companyId, UUID contractId, DiscountType discountType, BigDecimal discountValue) {
        contractRepository.findByIdAndCompanyIdWithBillingLock(contractId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Contract"));
        ContractDiscount discount = ContractDiscount.create(companyId, contractId, discountType, discountValue);
        var proposedAmount = ContractNetAmountCalculator.calculate(
                contractServiceRepository.findByContractIdAndCompanyId(contractId, companyId), discount
        );
        BigDecimal approvedAmount = approvedContractBillingAmountProvider.approvedNetAmount(companyId, contractId);
        if (proposedAmount.netAmount().compareTo(approvedAmount) < 0) {
            throw new IllegalStateException("Contract net amount must not be lower than its approved billing amount");
        }
        if (proposedAmount.contractDiscountAmount().compareTo(approvedContractBillingAmountProvider.approvedDiscountAmount(companyId, contractId)) < 0) {
            throw new IllegalStateException("Contract discount must cover its active negative billing allocations");
        }
        return contractDiscountRepository.save(discount);
    }
}
