package com.renovar.canteiro.io.platform.catalog.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlanPriceTest {

    @Test
    void acceptsAZeroPriceAndAnOpenEndedValidity() {
        assertDoesNotThrow(() -> PlanPrice.create(
                UUID.randomUUID(),
                new BigDecimal("0.00"),
                LocalDate.of(2026, 8, 1),
                null
        ));
    }

    @Test
    void rejectsPricesWithMoreThanTwoDecimalPlaces() {
        assertThrows(IllegalArgumentException.class, () -> PlanPrice.create(
                UUID.randomUUID(),
                new BigDecimal("99.999"),
                LocalDate.of(2026, 8, 1),
                null
        ));
    }

    @Test
    void rejectsAPricePeriodThatEndsBeforeItStarts() {
        assertThrows(IllegalArgumentException.class, () -> PlanPrice.create(
                UUID.randomUUID(),
                new BigDecimal("99.90"),
                LocalDate.of(2026, 8, 2),
                LocalDate.of(2026, 8, 1)
        ));
    }

    @Test
    void allowsEndingAPricePeriodAfterItsStart() {
        PlanPrice planPrice = PlanPrice.create(
                UUID.randomUUID(),
                new BigDecimal("99.90"),
                LocalDate.of(2026, 8, 1),
                null
        );

        assertDoesNotThrow(() -> planPrice.endOn(LocalDate.of(2026, 8, 31)));
    }
}
