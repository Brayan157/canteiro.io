package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.contracts.application.CalculateContractNetAmountUseCase;
import com.renovar.canteiro.io.contracts.domain.ContractNetAmount;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.contracts.domain.ContractService;
import com.renovar.canteiro.io.contracts.domain.ContractServiceRepository;
import com.renovar.canteiro.io.receivables.domain.BillingAllocation;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationRepository;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationStatus;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationType;
import com.renovar.canteiro.io.receivables.domain.ContractBillingBalance;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ValidateContractBillingLimitUseCase {

    private final ContractRepository contractRepository;
    private final ContractServiceRepository contractServiceRepository;
    private final BillingAllocationRepository billingAllocationRepository;
    private final CalculateContractNetAmountUseCase calculateContractNetAmountUseCase;
    private final CalculateContractBillingBalanceUseCase calculateContractBillingBalanceUseCase;

    @Transactional
    public void requireAvailableForActivation(UUID companyId, UUID contractId,
                                              Collection<BillingAllocation> allocations) {
        contractRepository.findByIdAndCompanyIdWithBillingLock(contractId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Contract"));
        List<BillingAllocation> activeAllocations = billingAllocationRepository
                .findByContractIdAndCompanyId(contractId, companyId).stream()
                .filter(allocation -> allocation.getStatus() == BillingAllocationStatus.ACTIVE)
                .toList();
        BigDecimal proposedAmount = allocations.stream()
                .peek(allocation -> requireSameContractAndTenant(allocation, companyId, contractId))
                .map(BillingAllocation::getAllocatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        ContractBillingBalance balance = calculateContractBillingBalanceUseCase.calculate(companyId, contractId);
        if (balance.activeAllocatedAmount().add(proposedAmount).compareTo(balance.contractNetAmount()) > 0) {
            throw new BillingLimitExceededException();
        }
        requireServiceCapacity(companyId, contractId, activeAllocations, allocations);
        requireDiscountCapacity(companyId, contractId, activeAllocations, allocations);
    }

    private void requireServiceCapacity(UUID companyId, UUID contractId,
                                        Collection<BillingAllocation> activeAllocations,
                                        Collection<BillingAllocation> proposedAllocations) {
        Map<UUID, ContractService> services = contractServiceRepository
                .findByContractIdAndCompanyId(contractId, companyId).stream()
                .collect(Collectors.toMap(ContractService::getId, Function.identity()));
        Map<UUID, BigDecimal> activeByService = amountsByService(activeAllocations);
        Map<UUID, BigDecimal> proposedByService = amountsByService(proposedAllocations);
        for (Map.Entry<UUID, BigDecimal> proposed : proposedByService.entrySet()) {
            ContractService service = services.get(proposed.getKey());
            if (service == null) {
                throw new TenantResourceNotFoundException("Contract service");
            }
            BigDecimal resultingAmount = activeByService.getOrDefault(proposed.getKey(), BigDecimal.ZERO)
                    .add(proposed.getValue()).setScale(2, RoundingMode.HALF_UP);
            if (resultingAmount.compareTo(service.getNetAmount()) > 0) {
                throw new BillingLimitExceededException("Service billing allocation exceeds the service net amount");
            }
        }
    }

    private void requireDiscountCapacity(UUID companyId, UUID contractId,
                                         Collection<BillingAllocation> activeAllocations,
                                         Collection<BillingAllocation> proposedAllocations) {
        BigDecimal activeDiscount = discountAmount(activeAllocations);
        BigDecimal proposedDiscount = discountAmount(proposedAllocations);
        ContractNetAmount contractAmount = calculateContractNetAmountUseCase.calculate(companyId, contractId);
        if (activeDiscount.add(proposedDiscount).compareTo(contractAmount.contractDiscountAmount()) > 0) {
            throw new BillingLimitExceededException("Contract discount allocation exceeds the contract discount amount");
        }
    }

    private Map<UUID, BigDecimal> amountsByService(Collection<BillingAllocation> allocations) {
        return allocations.stream()
                .filter(allocation -> allocation.getType() == BillingAllocationType.SERVICE)
                .collect(Collectors.groupingBy(BillingAllocation::getContractServiceId,
                        Collectors.reducing(BigDecimal.ZERO, BillingAllocation::getAllocatedAmount, BigDecimal::add)));
    }

    private BigDecimal discountAmount(Collection<BillingAllocation> allocations) {
        return allocations.stream()
                .filter(allocation -> allocation.getType() == BillingAllocationType.CONTRACT_DISCOUNT)
                .map(BillingAllocation::getAllocatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .abs().setScale(2, RoundingMode.HALF_UP);
    }

    private void requireSameContractAndTenant(BillingAllocation allocation, UUID companyId, UUID contractId) {
        if (!companyId.equals(allocation.getCompanyId()) || !contractId.equals(allocation.getContractId())) {
            throw new IllegalArgumentException("Billing allocation must belong to the target contract and company");
        }
    }
}
