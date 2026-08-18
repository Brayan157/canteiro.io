package com.renovar.canteiro.io.contracts.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContractRevisionTest {

    @Test
    void allowsNetAmountEqualToApprovedBilling() {
        ContractRevision revision = ContractRevision.create(
                UUID.randomUUID(), UUID.randomUUID(), 1, new BigDecimal("150.00"), new BigDecimal("100.00"),
                new BigDecimal("100.00"), "Commercial renegotiation"
        );

        assertEquals(new BigDecimal("100.00"), revision.getProposedNetAmount());
    }

    @Test
    void blocksReductionBelowApprovedBilling() {
        assertThrows(IllegalArgumentException.class, () -> ContractRevision.create(
                UUID.randomUUID(), UUID.randomUUID(), 1, new BigDecimal("150.00"), new BigDecimal("99.99"),
                new BigDecimal("100.00"), "Invalid reduction"
        ));
    }
}
