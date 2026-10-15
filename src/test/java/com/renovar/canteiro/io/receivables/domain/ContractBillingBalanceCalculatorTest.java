package com.renovar.canteiro.io.receivables.domain;

import com.renovar.canteiro.io.contracts.domain.ContractNetAmount;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContractBillingBalanceCalculatorTest {

    @Test
    void calculatesBalanceFromOnlyActiveAllocationsIncludingTheContractDiscountAdjustment() {
        UUID companyId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        UUID installmentId = UUID.randomUUID();
        BillingAllocation activeService = allocation(
                companyId, installmentId, contractId, UUID.randomUUID(), BillingAllocationType.SERVICE,
                new BigDecimal("120.00"), BillingAllocationStatus.ACTIVE
        );
        BillingAllocation activeDiscount = allocation(
                companyId, installmentId, contractId, null, BillingAllocationType.CONTRACT_DISCOUNT,
                new BigDecimal("-20.00"), BillingAllocationStatus.ACTIVE
        );
        BillingAllocation draftService = allocation(
                companyId, installmentId, contractId, UUID.randomUUID(), BillingAllocationType.SERVICE,
                new BigDecimal("30.00"), BillingAllocationStatus.DRAFT
        );
        BillingAllocation cancelledService = allocation(
                companyId, installmentId, contractId, UUID.randomUUID(), BillingAllocationType.SERVICE,
                new BigDecimal("10.00"), BillingAllocationStatus.CANCELLED
        );

        ContractBillingBalance balance = ContractBillingBalanceCalculator.calculate(
                new ContractNetAmount(new BigDecimal("120.00"), new BigDecimal("20.00"), new BigDecimal("100.00")),
                List.of(activeService, activeDiscount, draftService, cancelledService)
        );

        assertEquals(new BigDecimal("100.00"), balance.contractNetAmount());
        assertEquals(new BigDecimal("120.00"), balance.serviceSubtotal());
        assertEquals(new BigDecimal("20.00"), balance.contractDiscountAmount());
        assertEquals(new BigDecimal("100.00"), balance.activeAllocatedAmount());
        assertEquals(new BigDecimal("0.00"), balance.availableAmount());
    }

    private BillingAllocation allocation(UUID companyId, UUID installmentId, UUID contractId, UUID contractServiceId,
                                         BillingAllocationType type, BigDecimal amount, BillingAllocationStatus status) {
        return BillingAllocation.rehydrate(
                UUID.randomUUID(), companyId, installmentId, contractId, contractServiceId, type, amount, status,
                null, null
        );
    }
}
