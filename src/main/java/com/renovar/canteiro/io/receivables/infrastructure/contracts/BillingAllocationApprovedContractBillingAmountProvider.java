package com.renovar.canteiro.io.receivables.infrastructure.contracts;

import com.renovar.canteiro.io.contracts.application.ApprovedContractBillingAmountProvider;
import com.renovar.canteiro.io.receivables.application.CalculateContractBillingBalanceUseCase;
import com.renovar.canteiro.io.receivables.domain.BillingAllocation;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationRepository;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationStatus;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class BillingAllocationApprovedContractBillingAmountProvider implements ApprovedContractBillingAmountProvider {

    private final CalculateContractBillingBalanceUseCase calculateContractBillingBalanceUseCase;
    private final BillingAllocationRepository allocations;

    @Override
    public BigDecimal approvedNetAmount(UUID companyId, UUID contractId) {
        return calculateContractBillingBalanceUseCase.calculate(companyId, contractId).activeAllocatedAmount();
    }

    @Override
    public Map<UUID, BigDecimal> approvedServiceAmounts(UUID companyId, UUID contractId) {
        return allocations.findByContractIdAndCompanyId(contractId, companyId).stream()
                .filter(a -> a.getStatus() == BillingAllocationStatus.ACTIVE && a.getType() == BillingAllocationType.SERVICE)
                .collect(Collectors.groupingBy(BillingAllocation::getContractServiceId,
                        Collectors.reducing(BigDecimal.ZERO, BillingAllocation::getAllocatedAmount, BigDecimal::add)));
    }

    @Override
    public Map<UUID, BigDecimal> approvedBaseServiceAmounts(UUID companyId, UUID contractId) {
        return allocations.findByContractIdAndCompanyId(contractId, companyId).stream()
                .filter(a -> a.getStatus() == BillingAllocationStatus.ACTIVE && a.getType() == BillingAllocationType.SERVICE
                        && a.getMeasurementContractCompositionId() == null)
                .collect(Collectors.groupingBy(BillingAllocation::getContractServiceId,
                        Collectors.reducing(BigDecimal.ZERO, BillingAllocation::getAllocatedAmount, BigDecimal::add)));
    }

    @Override
    public BigDecimal approvedDiscountAmount(UUID companyId, UUID contractId) {
        return allocations.findByContractIdAndCompanyId(contractId, companyId).stream()
                .filter(a -> a.getStatus() == BillingAllocationStatus.ACTIVE && a.getType() == BillingAllocationType.CONTRACT_DISCOUNT)
                .map(BillingAllocation::getAllocatedAmount).reduce(BigDecimal.ZERO, BigDecimal::add).abs();
    }
}
