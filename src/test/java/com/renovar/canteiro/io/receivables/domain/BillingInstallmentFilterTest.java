package com.renovar.canteiro.io.receivables.domain;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BillingInstallmentFilterTest {
    @Test
    void normalizesOptionalReferenceWithoutInterpretingLiteralSearchCharacters() {
        UUID contract = UUID.randomUUID();
        var filter = new BillingInstallmentFilter(contract, "  PARC%_!  ", BillingInstallmentStatus.CANCELLED, null, null);
        assertEquals("PARC%_!", filter.reference());assertEquals(contract, filter.contractId());
        assertEquals(BillingInstallmentStatus.CANCELLED, filter.status());
        assertNull(new BillingInstallmentFilter(null, "  ", null, null, null).reference());
    }

    @Test
    void boundsTheReferenceSearch() {
        assertEquals(100, new BillingInstallmentFilter(null, "x".repeat(100), null, null, null).reference().length());
        assertThrows(IllegalArgumentException.class, () -> new BillingInstallmentFilter(null, "x".repeat(101), null, null, null));
    }

    @Test
    void acceptsInclusiveOrOpenDateRangesButRejectsAnInvertedRange() {
        LocalDate day = LocalDate.of(2026, 11, 1);
        assertEquals(day, new BillingInstallmentFilter(null, null, null, day, day).dueTo());
        assertEquals(day, new BillingInstallmentFilter(null, null, null, null, day).dueTo());
        assertThrows(IllegalArgumentException.class, () -> new BillingInstallmentFilter(null, null, null, day.plusDays(1), day));
    }
}
