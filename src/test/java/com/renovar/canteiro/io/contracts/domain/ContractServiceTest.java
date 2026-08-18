package com.renovar.canteiro.io.contracts.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ContractServiceTest {

    @Test
    void copiesTemplateContentIntoAnIndependentContractService() {
        ServiceTemplate template = ServiceTemplate.rehydrate(
                UUID.randomUUID(), UUID.randomUUID(), "Assembly", "Assembly service", true, null, null
        );

        ContractService service = ContractService.copyOf(template.getCompanyId(), UUID.randomUUID(), template);

        assertEquals(template.getId(), service.getSourceServiceTemplateId());
        assertEquals("Assembly", service.getName());
        assertEquals("Assembly service", service.getDescription());
        assertNull(service.getId());
        assertEquals(new BigDecimal("0.00"), service.getGrossAmount());
    }

    @Test
    void calculatesGrossAmountFromQuantityAndUnitPrice() {
        ContractService service = ContractService.create(
                UUID.randomUUID(), UUID.randomUUID(), "Assembly", null, ContractServiceStatus.ACTIVE,
                new BigDecimal("2.5000"), new BigDecimal("125.90")
        );
        assertEquals(new BigDecimal("314.75"), service.getGrossAmount());
    }

    @Test
    void appliesPercentageDiscountToTheServiceNetAmount() {
        ContractService service = ContractService.create(
                UUID.randomUUID(), UUID.randomUUID(), "Assembly", null, ContractServiceStatus.ACTIVE,
                new BigDecimal("2.0000"), new BigDecimal("100.00"), DiscountType.PERCENTAGE, new BigDecimal("12.5000")
        );
        assertEquals(new BigDecimal("25.00"), service.getDiscountAmount());
        assertEquals(new BigDecimal("175.00"), service.getNetAmount());
    }
}
