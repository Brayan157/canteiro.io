package com.renovar.canteiro.io.contracts.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContractDiscountTest {

    @Test
    void storesASeparatePercentageAdjustment() {
        ContractDiscount discount = ContractDiscount.create(
                UUID.randomUUID(), UUID.randomUUID(), DiscountType.PERCENTAGE, new BigDecimal("10")
        );

        assertEquals(DiscountType.PERCENTAGE, discount.getDiscountType());
        assertEquals(new BigDecimal("10.0000"), discount.getDiscountValue());
        assertEquals(true, discount.isActive());
    }

    @Test
    void reversesWithoutDiscardingTheDiscountIdentityOrValues() {
        ContractDiscount discount = ContractDiscount.create(
                UUID.randomUUID(), UUID.randomUUID(), DiscountType.FIXED, new BigDecimal("50.00")
        );

        ContractDiscount reversed = discount.reverse();

        assertEquals(false, reversed.isActive());
        assertEquals(DiscountType.FIXED, reversed.getDiscountType());
        assertEquals(new BigDecimal("50.00"), reversed.getDiscountValue());
        assertThrows(IllegalStateException.class, reversed::reverse);
    }

    @Test
    void rejectsPercentageHigherThanOneHundred() {
        assertThrows(IllegalArgumentException.class, () -> ContractDiscount.create(
                UUID.randomUUID(), UUID.randomUUID(), DiscountType.PERCENTAGE, new BigDecimal("100.0001")
        ));
    }
}
