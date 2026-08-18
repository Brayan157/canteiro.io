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
}
