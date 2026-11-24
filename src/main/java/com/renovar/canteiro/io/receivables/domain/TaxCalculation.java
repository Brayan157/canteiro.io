package com.renovar.canteiro.io.receivables.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Arithmetic only, never an incidence/rate/regime decision. Inputs are explicitly provided by the user. */
public record TaxCalculation(Mode mode, BigDecimal baseAmount, BigDecimal ratePercent, BigDecimal amount) {
    @io.swagger.v3.oas.annotations.media.Schema(name = "InvoiceTaxCalculationMode")
    public enum Mode { AMOUNT, PERCENTAGE }
    public TaxCalculation {
        if (mode == null) throw new IllegalArgumentException("Tax calculation mode is required");
        amount = money(amount);
        if (mode == Mode.AMOUNT) {
            if (baseAmount != null || ratePercent != null) throw new IllegalArgumentException("AMOUNT forbids base and rate");
        } else {
            baseAmount = money(baseAmount);
            if (ratePercent == null || ratePercent.signum() <= 0 || ratePercent.precision() - ratePercent.scale() > 17 || ratePercent.scale() > 4) throw new IllegalArgumentException("Provide a positive rate with at most four decimal places");
            ratePercent = ratePercent.setScale(4, RoundingMode.UNNECESSARY);
            var official = baseAmount.multiply(ratePercent).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
            if (official.compareTo(amount) != 0) throw new IllegalArgumentException("Tax amount differs from the official calculation");
        }
    }
    public static TaxCalculation calculate(Mode mode, BigDecimal amount, BigDecimal base, BigDecimal rate) {
        if (mode == Mode.PERCENTAGE) {
            if (amount != null || base == null || rate == null) throw new IllegalArgumentException("PERCENTAGE requires base and rate only");
            return new TaxCalculation(mode, base, rate, base.multiply(rate).movePointLeft(2).setScale(2, RoundingMode.HALF_UP));
        }
        return new TaxCalculation(mode, base, rate, amount);
    }
    private static BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.precision() - value.scale() > 17 || value.stripTrailingZeros().scale() > 2) throw new IllegalArgumentException("Positive money with two decimal places within supported precision is required");
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }
}
