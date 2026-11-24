package com.renovar.canteiro.io.receivables.domain;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaxCalculationTest {
    @Test void computesExplicitBaseAndFourPlaceRateOnlyRoundingFinalMoney() {
        var c = TaxCalculation.calculate(TaxCalculation.Mode.PERCENTAGE, null, new BigDecimal("1000.00"), new BigDecimal("2.1234"));
        assertEquals(new BigDecimal("21.23"), c.amount()); assertEquals(new BigDecimal("2.1234"), c.ratePercent());
        assertEquals(new BigDecimal("0.01"), TaxCalculation.calculate(TaxCalculation.Mode.PERCENTAGE, null, new BigDecimal("1.00"), new BigDecimal("0.5")).amount());
        assertEquals(new BigDecimal("10.00"), TaxCalculation.calculate(TaxCalculation.Mode.PERCENTAGE, null, new BigDecimal("1.00"), new BigDecimal("1000")).amount());
    }
    @Test void rejectsMixedInputsExcessPrecisionZeroAndDivergentStoredResult() {
        assertThrows(IllegalArgumentException.class, () -> TaxCalculation.calculate(TaxCalculation.Mode.AMOUNT, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> TaxCalculation.calculate(TaxCalculation.Mode.PERCENTAGE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> TaxCalculation.calculate(TaxCalculation.Mode.PERCENTAGE, null, BigDecimal.ONE, new BigDecimal("2.12345")));
        assertThrows(IllegalArgumentException.class, () -> TaxCalculation.calculate(TaxCalculation.Mode.PERCENTAGE, null, new BigDecimal("0.01"), new BigDecimal("0.0001")));
        assertThrows(IllegalArgumentException.class, () -> new TaxCalculation(TaxCalculation.Mode.PERCENTAGE, new BigDecimal("1000.00"), new BigDecimal("2.1234"), new BigDecimal("21.24")));
    }
    @Test void preservesExplicitMoneyWithoutInferringBaseOrRate() {
        var c = TaxCalculation.calculate(TaxCalculation.Mode.AMOUNT, new BigDecimal("9007199254740993.01"), null, null);
        assertEquals(new BigDecimal("9007199254740993.01"), c.amount());
    }
}
