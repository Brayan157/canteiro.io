package com.renovar.canteiro.io.measurements.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class MeasurementVersionAmountCalculator {

    private MeasurementVersionAmountCalculator() {
    }

    public static MeasurementVersionAmounts calculate(List<MeasurementItem> items, MeasurementDiscount discount) {
        BigDecimal totalWeightKg = items.stream()
                .filter(MeasurementItem::isActive)
                .map(MeasurementItem::getTotalWeightKg)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal grossAmount = items.stream()
                .filter(MeasurementItem::isActive)
                .map(MeasurementItem::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        return calculate(totalWeightKg, grossAmount, discount);
    }

    /**
     * Applies the version discount to a gross amount already accumulated from a
     * paged or streamed source, without requiring every item in memory.
     */
    public static MeasurementVersionAmounts calculate(BigDecimal grossAmount, MeasurementDiscount discount) {
        return calculate(BigDecimal.ZERO.setScale(4), grossAmount, discount);
    }

    public static MeasurementVersionAmounts calculate(BigDecimal totalWeightKg, BigDecimal grossAmount,
                                                        MeasurementDiscount discount) {
        grossAmount = grossAmount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountAmount = calculateDiscountAmount(grossAmount, discount);
        return new MeasurementVersionAmounts(totalWeightKg.setScale(4, RoundingMode.HALF_UP), grossAmount, discountAmount,
                grossAmount.subtract(discountAmount).setScale(2, RoundingMode.HALF_UP));
    }

    private static BigDecimal calculateDiscountAmount(BigDecimal grossAmount, MeasurementDiscount discount) {
        if (discount == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal amount = discount.getDiscountType() == MeasurementDiscountType.FIXED
                ? discount.getDiscountValue()
                : grossAmount.multiply(discount.getDiscountValue()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        if (amount.compareTo(grossAmount) > 0) {
            throw new IllegalArgumentException("Measurement discount must not exceed gross amount");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
