package com.renovar.canteiro.io.receivables.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaxObligationTest {

    @Test
    void createsAllSupportedTaxTreatmentsWithTheirRequiredLinks() {
        TaxObligation payable = TaxObligation.register(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, "ISS", null,
                LocalDate.of(2026, 8, 30), new BigDecimal("10.00"), TaxTreatment.PAYABLE_BY_COMPANY
        );
        TaxObligation withheld = TaxObligation.register(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "IRRF", null,
                null, new BigDecimal("5.00"), TaxTreatment.WITHHELD_BY_CUSTOMER
        );
        TaxObligation informational = TaxObligation.register(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, "INSS", null,
                null, new BigDecimal("3.00"), TaxTreatment.INFORMATIONAL
        );

        assertEquals(TaxTreatment.PAYABLE_BY_COMPANY, payable.getTreatment());
        assertEquals(TaxTreatment.WITHHELD_BY_CUSTOMER, withheld.getTreatment());
        assertEquals(TaxTreatment.INFORMATIONAL, informational.getTreatment());
    }

    @Test
    void rejectsIncompatibleTreatmentLinks() {
        assertThrows(IllegalArgumentException.class, () -> TaxObligation.register(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, "ISS", null,
                null, BigDecimal.ONE, TaxTreatment.PAYABLE_BY_COMPANY
        ));
        assertThrows(IllegalArgumentException.class, () -> TaxObligation.register(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, "IRRF", null,
                null, BigDecimal.ONE, TaxTreatment.WITHHELD_BY_CUSTOMER
        ));
    }
}
