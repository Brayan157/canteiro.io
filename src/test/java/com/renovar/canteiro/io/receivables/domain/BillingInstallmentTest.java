package com.renovar.canteiro.io.receivables.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BillingInstallmentTest {

    @Test
    void startsInstallmentsAndAllocationsAsDraftWithNormalizedMoney() {
        BillingInstallment installment = BillingInstallment.create(
                UUID.randomUUID(), UUID.randomUUID(), "  FAT-001 ", "  First installment ",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15)
        );
        BillingAllocation allocation = BillingAllocation.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("12.345")
        );

        assertEquals("FAT-001", installment.getReference());
        assertEquals(BillingInstallmentStatus.DRAFT, installment.getStatus());
        assertEquals(new BigDecimal("12.35"), allocation.getAllocatedAmount());
        assertEquals(BillingAllocationType.SERVICE, allocation.getType());
        assertEquals(BillingAllocationStatus.DRAFT, allocation.getStatus());
    }

    @Test
    void rejectsAnInstallmentDueBeforeItsIssueDate() {
        assertThrows(IllegalArgumentException.class, () -> BillingInstallment.create(
                UUID.randomUUID(), UUID.randomUUID(), null, null,
                LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 14)
        ));
    }

    @Test
    void rejectsZeroOrNegativeServiceAllocations() {
        assertThrows(IllegalArgumentException.class, () -> BillingAllocation.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ZERO
        ));
        assertThrows(IllegalArgumentException.class, () -> BillingAllocation.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("-0.01")
        ));
    }

    @Test
    void keepsContractDiscountSeparateFromServicesAsANegativeAllocation() {
        BillingAllocation discount = BillingAllocation.createContractDiscount(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("-15.123")
        );

        assertEquals(BillingAllocationType.CONTRACT_DISCOUNT, discount.getType());
        assertEquals(new BigDecimal("-15.12"), discount.getAllocatedAmount());
        assertNull(discount.getContractServiceId());
        assertThrows(IllegalArgumentException.class, () -> BillingAllocation.createContractDiscount(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("15.00")
        ));
    }

    @Test
    void activatesOnlyDraftAllocations() {
        BillingAllocation draft = BillingAllocation.createService(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("15.00")
        );

        assertEquals(BillingAllocationStatus.ACTIVE, draft.activate().getStatus());
        assertThrows(IllegalStateException.class, () -> BillingAllocation.rehydrate(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                BillingAllocationType.SERVICE, new BigDecimal("15.00"), BillingAllocationStatus.CANCELLED, null, null
        ).activate());
    }
}
