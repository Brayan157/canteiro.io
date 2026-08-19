package com.renovar.canteiro.io.measurements.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class MeasurementVersionAmountCalculator {

    private MeasurementVersionAmountCalculator() {
    }

    public static MeasurementVersionAmounts calculate(List<MeasurementItem> items, MeasurementDiscount discount) {
        BigDecimal grossAmount = items.stream()
                .map(MeasurementItem::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountAmount = calculateDiscountAmount(grossAmount, discount);
        return new MeasurementVersionAmounts(grossAmount, discountAmount,
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
