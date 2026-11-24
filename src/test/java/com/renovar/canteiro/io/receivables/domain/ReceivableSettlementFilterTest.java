package com.renovar.canteiro.io.receivables.domain;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReceivableSettlementFilterTest {
    @Test void acceptsAbsentOrInclusiveSameDayBounds() {
        assertNull(new ReceivableSettlementFilter(null, null, null, null, null).entryType());
        LocalDate day = LocalDate.of(2026, 10, 26);
        var filter = new ReceivableSettlementFilter(ReceivableSettlementEntryType.REVERSAL, ReceivableSettlementStatus.CONFIRMED,
                ReceivableSettlementPaymentMethod.PIX, day, day);
        assertEquals(day, filter.receivedTo()); assertEquals(ReceivableSettlementEntryType.REVERSAL, filter.entryType());
    }

    @Test void rejectsInvertedBusinessDateRange() {
        assertThrows(IllegalArgumentException.class, () -> new ReceivableSettlementFilter(null, null, null,
                LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 25)));
    }
}
