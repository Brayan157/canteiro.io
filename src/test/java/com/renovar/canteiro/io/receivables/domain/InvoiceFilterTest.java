package com.renovar.canteiro.io.receivables.domain;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InvoiceFilterTest {
    @Test void normalizesBusinessTextAndKeepsLiteralCharacters() {
        var filter = new InvoiceFilter(null, " NF%_! ", " ", " Contrato ", " Parcela ", null, null, null);
        assertEquals("NF%_!", filter.number());
        assertNull(filter.series());
        assertEquals("Contrato", filter.contractSearch());
        assertEquals("Parcela", filter.installmentReference());
    }

    @Test void rejectsOverlongFiltersAndInvertedDates() {
        assertThrows(IllegalArgumentException.class, () -> new InvoiceFilter(null, "x".repeat(101), null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new InvoiceFilter(null, null, "x".repeat(51), null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new InvoiceFilter(null, null, null, "x".repeat(201), null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new InvoiceFilter(null, null, null, null, "x".repeat(101), null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new InvoiceFilter(null, null, null, null, null, null,
                LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 24)));
    }
}
