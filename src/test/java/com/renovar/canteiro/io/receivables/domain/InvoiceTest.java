package com.renovar.canteiro.io.receivables.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InvoiceTest {

    @Test
    void registersInvoiceWithPositiveCalculatedTotal() {
        Invoice invoice = Invoice.register(
                UUID.randomUUID(), UUID.randomUUID(), "NF-001", "1", "access-key", LocalDate.of(2026, 8, 20),
                new BigDecimal("123.456")
        );

        assertEquals(InvoiceStatus.REGISTERED, invoice.getStatus());
        assertEquals(new BigDecimal("123.46"), invoice.getTotalAmount());
    }

    @Test
    void rejectsBlankNumberAndNonPositiveTotal() {
        assertThrows(IllegalArgumentException.class, () -> Invoice.register(
                UUID.randomUUID(), UUID.randomUUID(), " ", null, null, LocalDate.now(), BigDecimal.ONE
        ));
        assertThrows(IllegalArgumentException.class, () -> Invoice.register(
                UUID.randomUUID(), UUID.randomUUID(), "NF-001", null, null, LocalDate.now(), BigDecimal.ZERO
        ));
    }

    @Test
    void rejectsZeroInvoiceItemAmount() {
        assertThrows(IllegalArgumentException.class, () -> InvoiceItem.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, BigDecimal.ZERO
        ));
    }
}
