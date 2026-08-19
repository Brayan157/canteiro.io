package com.renovar.canteiro.io.measurements.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MeasurementVersionAmountCalculatorTest {

    @Test
    void appliesHeaderDiscountWithoutChangingItemAmounts() {
        UUID companyId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        MeasurementItem item = MeasurementItem.createSquareMeter(
                companyId, versionId, 1, "Assembly", null, new BigDecimal("10.0000"), new BigDecimal("10.00")
        );
        MeasurementDiscount discount = MeasurementDiscount.create(
                companyId, versionId, MeasurementDiscountType.PERCENTAGE, new BigDecimal("12.5000")
        );

        MeasurementVersionAmounts amounts = MeasurementVersionAmountCalculator.calculate(List.of(item), discount);

        assertEquals(new BigDecimal("100.00"), item.getTotalAmount());
        assertEquals(new BigDecimal("100.00"), amounts.grossAmount());
        assertEquals(new BigDecimal("12.50"), amounts.discountAmount());
        assertEquals(new BigDecimal("87.50"), amounts.netAmount());
    }

    @Test
    void rejectsHeaderDiscountGreaterThanTheItemGrossAmount() {
        UUID companyId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        MeasurementItem item = MeasurementItem.createLinearMeter(
                companyId, versionId, 1, "Assembly", null, new BigDecimal("10.0000"), new BigDecimal("10.00")
        );
        MeasurementDiscount discount = MeasurementDiscount.create(
                companyId, versionId, MeasurementDiscountType.FIXED, new BigDecimal("100.01")
        );

        assertThrows(IllegalArgumentException.class,
                () -> MeasurementVersionAmountCalculator.calculate(List.of(item), discount));
    }
}
