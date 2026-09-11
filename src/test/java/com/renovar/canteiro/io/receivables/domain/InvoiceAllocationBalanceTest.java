package com.renovar.canteiro.io.receivables.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoiceAllocationBalanceTest {

    @Test
    void tracksTheRemainingServiceAllocationAcrossPartialInvoices() {
        UUID allocationId = UUID.randomUUID();
        BillingAllocation allocation = BillingAllocation.rehydrate(
                allocationId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                BillingAllocationType.SERVICE, new BigDecimal("100.00"), BillingAllocationStatus.ACTIVE,
                Instant.now(), Instant.now()
        );

        InvoiceAllocationBalance balance = InvoiceAllocationBalance.from(allocation, new BigDecimal("40.00"));

        assertEquals(new BigDecimal("60.00"), balance.availableInvoiceAmount());
        assertTrue(balance.canRegister(new BigDecimal("60.00")));
        assertFalse(balance.canRegister(new BigDecimal("60.01")));
    }
}
