package com.renovar.canteiro.io.contracts.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContractNetAmountCalculatorTest {

    @Test
    void calculatesTheNetAmountWithoutDistributingContractDiscountToServices() {
        UUID companyId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        ContractService service = ContractService.create(
                companyId, contractId, "Assembly", null, ContractServiceStatus.ACTIVE,
                new BigDecimal("2.0000"), new BigDecimal("100.00"), DiscountType.PERCENTAGE, new BigDecimal("10")
        );
        ContractDiscount discount = ContractDiscount.create(
                companyId, contractId, DiscountType.FIXED, new BigDecimal("50.00")
        );

        ContractNetAmount amount = ContractNetAmountCalculator.calculate(List.of(service), discount);

        assertEquals(new BigDecimal("180.00"), amount.serviceSubtotal());
        assertEquals(new BigDecimal("50.00"), amount.contractDiscountAmount());
        assertEquals(new BigDecimal("130.00"), amount.netAmount());
        assertEquals(new BigDecimal("180.00"), service.getNetAmount());
    }

    @Test
    void blocksContractDiscountHigherThanTheServiceSubtotal() {
        UUID companyId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        ContractService service = ContractService.create(
                companyId, contractId, "Assembly", null, ContractServiceStatus.ACTIVE,
                BigDecimal.ONE, new BigDecimal("100.00")
        );
        ContractDiscount discount = ContractDiscount.create(
                companyId, contractId, DiscountType.FIXED, new BigDecimal("100.01")
        );

        assertThrows(IllegalArgumentException.class, () -> ContractNetAmountCalculator.calculate(List.of(service), discount));
    }

    @Test
    void includesASeparateMeasurementHeaderAdjustmentWithoutChangingServicePrices() {
        ContractService service = ContractService.create(
                UUID.randomUUID(), UUID.randomUUID(), "Roofing", null, ContractServiceStatus.ACTIVE,
                BigDecimal.ONE, new BigDecimal("100.00")
        );

        ContractNetAmount amount = ContractNetAmountCalculator.calculate(
                List.of(service), null, new BigDecimal("10.00")
        );

        assertEquals(new BigDecimal("100.00"), amount.serviceSubtotal());
        assertEquals(new BigDecimal("10.00"), amount.contractDiscountAmount());
        assertEquals(new BigDecimal("90.00"), amount.netAmount());
        assertEquals(new BigDecimal("100.00"), service.getNetAmount());
    }
}
