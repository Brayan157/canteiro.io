package com.renovar.canteiro.io.measurements.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MeasurementFinancialStatusCalculatorTest {

    @Test
    void calculatesBilledAndBalanceOnlyFromOriginatedServices() {
        MeasurementOriginatedServiceFinancialPosition first = MeasurementFinancialStatusCalculator.position(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100.00"), new BigDecimal("40.00")
        );
        MeasurementOriginatedServiceFinancialPosition second = MeasurementFinancialStatusCalculator.position(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("80.00"), new BigDecimal("20.00")
        );

        MeasurementFinancialStatusCalculator.Totals totals = MeasurementFinancialStatusCalculator.totals(List.of(first, second));

        assertEquals(new BigDecimal("180.00"), totals.originatedServiceAmount());
        assertEquals(new BigDecimal("60.00"), totals.billedAmount());
        assertEquals(new BigDecimal("120.00"), totals.balanceAmount());
    }

    @Test
    void rejectsBilledAmountGreaterThanTheOriginatedServiceAmount() {
        assertThrows(IllegalArgumentException.class, () -> MeasurementFinancialStatusCalculator.position(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100.00"), new BigDecimal("100.01")
        ));
    }
}
