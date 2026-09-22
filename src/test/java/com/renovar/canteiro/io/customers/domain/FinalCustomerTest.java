package com.renovar.canteiro.io.customers.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FinalCustomerTest {

    @Test
    void normalizesTheDocumentAccordingToTheCustomerType() {
        FinalCustomer finalCustomer = FinalCustomer.create(
                UUID.randomUUID(), FinalCustomerType.LEGAL, "Client", "12.345.678/0001-90"
        );

        assertEquals("12345678000190", finalCustomer.getDocument());
    }

    @Test
    void rejectsADocumentThatDoesNotMatchTheCustomerType() {
        assertThrows(IllegalArgumentException.class, () -> FinalCustomer.create(
                UUID.randomUUID(), FinalCustomerType.INDIVIDUAL, "Client", "12.345.678/0001-90"
        ));
    }

    @Test
    void updatesTheCommercialProfileWithoutChangingTheTenantOrIdentifier() {
        FinalCustomer customer = FinalCustomer.rehydrate(
                UUID.randomUUID(), UUID.randomUUID(), FinalCustomerType.INDIVIDUAL, "Ana Silva", "529.982.247-25",
                true, null, null
        );

        FinalCustomer updated = customer.updateProfile(FinalCustomerType.LEGAL, "Usina Horizonte", "12.345.678/0001-90");

        assertEquals(customer.getId(), updated.getId());
        assertEquals(customer.getCompanyId(), updated.getCompanyId());
        assertEquals(FinalCustomerType.LEGAL, updated.getCustomerType());
        assertEquals("Usina Horizonte", updated.getName());
        assertEquals("12345678000190", updated.getDocument());
    }
}
