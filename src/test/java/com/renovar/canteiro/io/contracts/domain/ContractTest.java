package com.renovar.canteiro.io.contracts.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ContractTest {

    @Test
    void requiresAWork() {
        assertThrows(IllegalArgumentException.class, () -> Contract.create(
                UUID.randomUUID(), null, null, "Commercial agreement", ContractStatus.DRAFT, null, null, null
        ));
    }

    @Test
    void supportsMeasurementBasedCommercialFormation() {
        Contract contract = Contract.create(UUID.randomUUID(), UUID.randomUUID(), "MED-2026", ContractPricingMode.MEASUREMENT_BASED,
                "M-01", "Contrato por medição", ContractStatus.ACTIVE, null, null, null);
        assertEquals(ContractPricingMode.MEASUREMENT_BASED, contract.getPricingMode());
    }
}
