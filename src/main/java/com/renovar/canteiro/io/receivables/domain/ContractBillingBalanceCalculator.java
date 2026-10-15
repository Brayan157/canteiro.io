package com.renovar.canteiro.io.receivables.domain;

import com.renovar.canteiro.io.contracts.domain.ContractNetAmount;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

public final class ContractBillingBalanceCalculator {

    private ContractBillingBalanceCalculator() {
    }

    public static ContractBillingBalance calculate(ContractNetAmount contractAmount,
                                                   Collection<BillingAllocation> allocations) {
        BigDecimal netAmount = money(contractAmount.netAmount());
        BigDecimal activeAllocatedAmount = allocations.stream()
                .filter(allocation -> allocation.getStatus() == BillingAllocationStatus.ACTIVE)
                .map(BillingAllocation::getAllocatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        activeAllocatedAmount = money(activeAllocatedAmount);
        return new ContractBillingBalance(
                money(contractAmount.serviceSubtotal()),
                money(contractAmount.contractDiscountAmount()),
                netAmount,
                activeAllocatedAmount,
                netAmount.subtract(activeAllocatedAmount).setScale(2, RoundingMode.HALF_UP)
        );
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
