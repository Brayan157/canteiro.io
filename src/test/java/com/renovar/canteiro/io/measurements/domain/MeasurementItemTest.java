package com.renovar.canteiro.io.measurements.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MeasurementItemTest {

    @Test
    void calculatesSquareMeterAmountFromAreaAndPrice() {
        UUID companyId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();

        MeasurementItem item = MeasurementItem.createSquareMeter(
                companyId, versionId, 1, "Assembly", null, new BigDecimal("10.1234"), new BigDecimal("12.34")
        );

        assertEquals(MeasurementChargeType.SQUARE_METER, item.getChargeType());
        assertEquals(new BigDecimal("10.1234"), item.getAreaSquareMeters());
        assertEquals(new BigDecimal("12.34"), item.getUnitPrice());
        assertEquals(new BigDecimal("124.92"), item.getTotalAmount());
        assertEquals("area_square_meters × unit_price", item.getCalculationFormula());
        assertThrows(IllegalArgumentException.class,
                () -> MeasurementItem.createSquareMeter(companyId, versionId, 0, "Assembly", null,
                        BigDecimal.ONE, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,
                () -> MeasurementItem.createSquareMeter(companyId, versionId, 1, " ", null,
                        BigDecimal.ONE, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,
                () -> MeasurementItem.createSquareMeter(companyId, versionId, 1, "Assembly", null,
                        BigDecimal.ZERO, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,
                () -> MeasurementItem.createSquareMeter(companyId, versionId, 1, "Assembly", null,
                        BigDecimal.ONE, new BigDecimal("-0.01")));
    }

    @Test
    void calculatesLinearMeterAmountFromMetersAndPrice() {
        MeasurementItem item = MeasurementItem.createLinearMeter(
                UUID.randomUUID(), UUID.randomUUID(), 1, "Assembly", null,
                new BigDecimal("42.1250"), new BigDecimal("8.90")
        );

        assertEquals(MeasurementChargeType.LINEAR_METER, item.getChargeType());
        assertEquals(new BigDecimal("42.1250"), item.getLinearMeters());
        assertEquals(new BigDecimal("374.91"), item.getTotalAmount());
        assertEquals("linear_meters × unit_price", item.getCalculationFormula());
        assertThrows(IllegalArgumentException.class,
                () -> MeasurementItem.createLinearMeter(UUID.randomUUID(), UUID.randomUUID(), 1, "Assembly", null,
                        BigDecimal.ZERO, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,
                () -> MeasurementItem.createLinearMeter(UUID.randomUUID(), UUID.randomUUID(), 1, "Assembly", null,
                        BigDecimal.ONE, new BigDecimal("-0.01")));
    }

    @Test
    void calculatesKilogramPerSquareMeterWeightAndAmount() {
        MeasurementItem item = MeasurementItem.createKilogramPerSquareMeter(
                UUID.randomUUID(), UUID.randomUUID(), 1, "Fabrication", null,
                new BigDecimal("2.5000"), new BigDecimal("10.0000"), new BigDecimal("12.34")
        );

        assertEquals(MeasurementChargeType.KILOGRAM_PER_SQUARE_METER, item.getChargeType());
        assertEquals(new BigDecimal("25.0000"), item.getTotalWeightKg());
        assertEquals(new BigDecimal("308.50"), item.getTotalAmount());
        assertThrows(IllegalArgumentException.class,
                () -> MeasurementItem.createKilogramPerSquareMeter(UUID.randomUUID(), UUID.randomUUID(), 1,
                        "Fabrication", null, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,
                () -> MeasurementItem.createKilogramPerSquareMeter(UUID.randomUUID(), UUID.randomUUID(), 1,
                        "Fabrication", null, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE));
    }

    @Test
    void calculatesKilogramPerLinearMeterWeightAndAmount() {
        MeasurementItem item = MeasurementItem.createKilogramPerLinearMeter(
                UUID.randomUUID(), UUID.randomUUID(), 1, "Assembly", null,
                new BigDecimal("3.2500"), new BigDecimal("8.0000"), new BigDecimal("12.34")
        );

        assertEquals(MeasurementChargeType.KILOGRAM_PER_LINEAR_METER, item.getChargeType());
        assertEquals(new BigDecimal("26.0000"), item.getTotalWeightKg());
        assertEquals(new BigDecimal("320.84"), item.getTotalAmount());
        assertThrows(IllegalArgumentException.class,
                () -> MeasurementItem.createKilogramPerLinearMeter(UUID.randomUUID(), UUID.randomUUID(), 1,
                        "Assembly", null, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,
                () -> MeasurementItem.createKilogramPerLinearMeter(UUID.randomUUID(), UUID.randomUUID(), 1,
                        "Assembly", null, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE));
    }
}
