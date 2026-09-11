package com.renovar.canteiro.io.receivables.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceivableBalanceTest {

    @Test
    void derivesPartialFullAndOverdueStatusesFromConfirmedSettlements() {
        Receivable receivable = Receivable.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15), new BigDecimal("100.00")
        );
        ReceivableBalance partial = new ReceivableBalance(receivable.getId() == null ? UUID.randomUUID() : receivable.getId(),
                receivable.getOriginalAmount(), new BigDecimal("40.00"));

        assertEquals(new BigDecimal("60.00"), partial.outstandingAmount());
        assertTrue(partial.canSettle(new BigDecimal("60.00")));
        assertFalse(partial.canSettle(new BigDecimal("60.01")));
        assertEquals(ReceivableStatus.PARTIALLY_RECEIVED, partial.statusOn(receivable, LocalDate.of(2026, 8, 15)));
        assertEquals(ReceivableStatus.OVERDUE, partial.statusOn(receivable, LocalDate.of(2026, 8, 16)));
        assertEquals(ReceivableStatus.RECEIVED, partial.afterSettlement(new BigDecimal("60.00"))
                .statusOn(receivable, LocalDate.of(2026, 8, 16)));
    }

    @Test
    void preventsConfirmedSettlementsAboveTheOriginalAmount() {
        assertThrows(IllegalArgumentException.class, () -> new ReceivableBalance(
                UUID.randomUUID(), new BigDecimal("100.00"), new BigDecimal("100.01")
        ));
    }
}
