package com.renovar.canteiro.io.receivables.domain;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReceivableFilterTest {
    @Test void normalizesFragmentsWithoutInterpretingWildcards() {
        var f = new ReceivableFilter(" 100%_! ", " REC ", " ", " Obra ", null, " Parcela ", null, null, null, null, null, null, null);
        assertEquals("100%_!", f.search()); assertEquals("REC", f.reference()); assertNull(f.contractSearch());
        assertEquals("Obra", f.workSearch()); assertEquals("Parcela", f.billingReference()); assertNull(f.customerName());
    }

    @Test void rejectsOversizeFiltersAndInvertedDates() {
        assertThrows(IllegalArgumentException.class, () -> new ReceivableFilter("x".repeat(201), null, null, null, null, null, null, null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new ReceivableFilter(null, "x".repeat(101), null, null, null, null, null, null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new ReceivableFilter(null, null, null, null, null, null, null, null, null, null, null, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 1)));
    }
}
