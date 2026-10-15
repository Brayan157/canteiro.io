package com.renovar.canteiro.io.measurements.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups persisted, official version lines without allocating a header discount.
 * A service reports weight only when at least one active line has a weight.
 */
public final class MeasurementServiceSummaryCalculator {

    private MeasurementServiceSummaryCalculator() {
    }

    public static List<MeasurementServiceSummary> calculate(List<MeasurementItem> items) {
        Map<String, Accumulator> groups = new LinkedHashMap<>();
        items.stream()
                .filter(MeasurementItem::isActive)
                .sorted(Comparator.comparing(MeasurementItem::getServiceTitle, String.CASE_INSENSITIVE_ORDER)
                        .thenComparingInt(MeasurementItem::getItemNumber))
                .forEach(item -> groups.computeIfAbsent(item.getServiceTitle(), ignored -> new Accumulator())
                        .add(item));
        return groups.entrySet().stream()
                .map(entry -> entry.getValue().toSummary(entry.getKey()))
                .toList();
    }

    private static final class Accumulator {
        private int itemCount;
        private BigDecimal grossAmount = BigDecimal.ZERO;
        private BigDecimal totalWeightKg;

        void add(MeasurementItem item) {
            itemCount++;
            grossAmount = grossAmount.add(item.getTotalAmount());
            if (item.getTotalWeightKg() != null) {
                totalWeightKg = totalWeightKg == null ? item.getTotalWeightKg() : totalWeightKg.add(item.getTotalWeightKg());
            }
        }

        MeasurementServiceSummary toSummary(String serviceTitle) {
            return new MeasurementServiceSummary(serviceTitle, itemCount,
                    grossAmount.setScale(2, RoundingMode.HALF_UP),
                    totalWeightKg == null ? null : totalWeightKg.setScale(4, RoundingMode.HALF_UP));
        }
    }
}
