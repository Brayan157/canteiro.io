package com.renovar.canteiro.io.receivables.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReceivableTest {

    @Test
    void createsAnOpenIndividualTitleWithItsFinancialGroup() {
        UUID groupId = UUID.randomUUID();
        Receivable receivable = Receivable.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), groupId, "REC-001", "First installment",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15), new BigDecimal("123.456")
        );

        assertEquals(groupId, receivable.getFinancialGroupId());
        assertEquals(ReceivableStatus.OPEN, receivable.getStatus());
        assertEquals(new BigDecimal("123.46"), receivable.getOriginalAmount());
    }

    @Test
    void rejectsNonPositiveAmountsAndDueDatesBeforeIssue() {
        assertThrows(IllegalArgumentException.class, () -> Receivable.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null,
                null, LocalDate.now(), BigDecimal.ZERO
        ));
        assertThrows(IllegalArgumentException.class, () -> Receivable.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null,
                LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 1), BigDecimal.ONE
        ));
    }
}
