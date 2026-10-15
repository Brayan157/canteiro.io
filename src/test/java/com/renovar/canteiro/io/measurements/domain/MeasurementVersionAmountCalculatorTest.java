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

    @Test
    void sumsOfficialWeightAndAmountAcrossMeasurementItems() {
        UUID companyId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        MeasurementItem weighted = MeasurementItem.createKilogramPerLinearMeter(
                companyId, versionId, 1, "Tubulação", "Fabricar", "Linha principal",
                java.time.LocalDate.of(2026, 6, 4), new BigDecimal("128.0000"),
                new BigDecimal("12.0000"), new BigDecimal("3.00"));
        MeasurementItem linear = MeasurementItem.createLinearMeter(
                companyId, versionId, 2, "Tubulação", "Instalar", "Trecho final",
                java.time.LocalDate.of(2026, 6, 5), new BigDecimal("208.0000"), new BigDecimal("200.00"));

        MeasurementVersionAmounts amounts = MeasurementVersionAmountCalculator.calculate(
                List.of(weighted, linear), null);

        assertEquals(new BigDecimal("1536.0000"), amounts.totalWeightKg());
        assertEquals(new BigDecimal("46208.00"), amounts.grossAmount());
        assertEquals(new BigDecimal("46208.00"), amounts.netAmount());
    }

    @Test
    void excludesLogicallyRemovedDraftItemsFromOfficialTotals() {
        UUID companyId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        MeasurementItem active = MeasurementItem.rehydrate(UUID.randomUUID(), companyId, versionId, 1,
                "Serviço", "Atividade", null, java.time.LocalDate.of(2026, 6, 4), MeasurementChargeType.LINEAR_METER,
                null, new BigDecimal("10.0000"), null, null, new BigDecimal("10.00"), null,
                new BigDecimal("100.00"), "linear_meters × unit_price", true, null, null, null);
        MeasurementItem removed = MeasurementItem.rehydrate(UUID.randomUUID(), companyId, versionId, 2,
                "Serviço", "Atividade", null, java.time.LocalDate.of(2026, 6, 4), MeasurementChargeType.LINEAR_METER,
                null, new BigDecimal("5.0000"), null, null, new BigDecimal("10.00"), null,
                new BigDecimal("50.00"), "linear_meters × unit_price", true, null, null, null).deactivate();

        MeasurementVersionAmounts amounts = MeasurementVersionAmountCalculator.calculate(List.of(active, removed), null);

        assertEquals(new BigDecimal("100.00"), amounts.grossAmount());
        assertEquals(new BigDecimal("100.00"), amounts.netAmount());
    }

    @Test
    void groupsOnlyActiveOfficialItemsByServiceWithoutAllocatingHeaderDiscount() {
        UUID companyId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        MeasurementItem weighted = MeasurementItem.createKilogramPerLinearMeter(companyId, versionId, 1,
                "Linha de vinhaça", "Fabricar", null, java.time.LocalDate.of(2026, 6, 4),
                new BigDecimal("128.0000"), new BigDecimal("12.0000"), new BigDecimal("3.00"));
        MeasurementItem linear = MeasurementItem.createLinearMeter(companyId, versionId, 2,
                "Linha de vinhaça", "Instalar", null, java.time.LocalDate.of(2026, 6, 4),
                new BigDecimal("2.0000"), new BigDecimal("10.00"));
        MeasurementItem removed = MeasurementItem.rehydrate(UUID.randomUUID(), companyId, versionId, 3,
                "Linha de vinhaça", "Removida", null, java.time.LocalDate.of(2026, 6, 4), MeasurementChargeType.LINEAR_METER,
                null, new BigDecimal("1.0000"), null, null, new BigDecimal("99.00"), null,
                new BigDecimal("99.00"), "linear_meters × unit_price", true, null, null, null).deactivate();
        MeasurementItem nonWeighted = MeasurementItem.createSquareMeter(companyId, versionId, 4,
                "Pintura", "Pintar", null, java.time.LocalDate.of(2026, 6, 4),
                new BigDecimal("3.0000"), new BigDecimal("20.00"));

        List<MeasurementServiceSummary> summaries = MeasurementServiceSummaryCalculator.calculate(
                List.of(weighted, linear, removed, nonWeighted));

        assertEquals(2, summaries.size());
        assertEquals("Linha de vinhaça", summaries.getFirst().serviceTitle());
        assertEquals(2, summaries.getFirst().itemCount());
        assertEquals(new BigDecimal("4628.00"), summaries.getFirst().grossAmount());
        assertEquals(new BigDecimal("1536.0000"), summaries.getFirst().totalWeightKg());
        assertEquals("Pintura", summaries.get(1).serviceTitle());
        assertEquals(new BigDecimal("60.00"), summaries.get(1).grossAmount());
        assertEquals(null, summaries.get(1).totalWeightKg());
    }
}
