package com.renovar.canteiro.io.measurements.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class MeasurementFinancialStatusCalculator {

    private MeasurementFinancialStatusCalculator() {
    }

    public static MeasurementOriginatedServiceFinancialPosition position(java.util.UUID measurementItemId,
                                                                           java.util.UUID contractServiceId,
                                                                           BigDecimal serviceAmount,
                                                                           BigDecimal billedAmount) {
        BigDecimal amount = money(serviceAmount, "Originated service amount is required");
        BigDecimal billed = money(billedAmount, "Originated service billed amount is required");
        if (billed.compareTo(amount) > 0) {
            throw new IllegalArgumentException("Originated service billed amount must not exceed its amount");
        }
        return new MeasurementOriginatedServiceFinancialPosition(measurementItemId, contractServiceId, amount, billed,
                amount.subtract(billed).setScale(2, RoundingMode.HALF_UP));
    }

    public static Totals totals(List<MeasurementOriginatedServiceFinancialPosition> positions) {
        BigDecimal amount = positions.stream().map(MeasurementOriginatedServiceFinancialPosition::serviceAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal billed = positions.stream().map(MeasurementOriginatedServiceFinancialPosition::billedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        return new Totals(amount, billed, amount.subtract(billed).setScale(2, RoundingMode.HALF_UP));
    }

    private static BigDecimal money(BigDecimal value, String message) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(message);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public record Totals(BigDecimal originatedServiceAmount, BigDecimal billedAmount, BigDecimal balanceAmount) {
    }
}
