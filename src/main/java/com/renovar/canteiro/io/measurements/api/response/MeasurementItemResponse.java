package com.renovar.canteiro.io.measurements.api.response;

import com.renovar.canteiro.io.measurements.domain.MeasurementChargeType;
import com.renovar.canteiro.io.measurements.domain.MeasurementItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

public record MeasurementItemResponse(
        UUID id,
        int itemNumber,
        String serviceTitle,
        String activity,
        String description,
        LocalDate measuredOn,
        @Schema(description = "UNIT uses unitQuantity × unitPrice and has null totalWeightKg.") MeasurementChargeType chargeType,
        BigDecimal areaSquareMeters,
        BigDecimal linearMeters,
        BigDecimal kilogramsPerSquareMeter,
        BigDecimal kilogramsPerLinearMeter,
        @Schema(example = "12.5000", description = "For UNIT, drives unitQuantity × unitPrice. For other charge types, optional operational count of executed pieces only; it does not affect formula, weight or amount.") BigDecimal unitQuantity,
        @Schema(type = "string", format = "decimal", example = "25.00", pattern = "^-?\\d+\\.\\d{2}$") String unitPrice,
        @Schema(type = "string", format = "decimal", example = "1536.0000", pattern = "^-?\\d+\\.\\d{4}$") String totalWeightKg,
        @Schema(type = "string", format = "decimal", example = "1250.50", pattern = "^-?\\d+\\.\\d{2}$") String totalAmount,
        String calculationFormula,
        boolean active,
        UUID sourceItemId
) {
    public static MeasurementItemResponse from(MeasurementItem item) {
        return item == null ? null : new MeasurementItemResponse(item.getId(), item.getItemNumber(), item.getServiceTitle(),
                item.getActivity(), item.getDescription(), item.getMeasuredOn(), item.getChargeType(),
                item.getAreaSquareMeters(), item.getLinearMeters(),
                item.getKilogramsPerSquareMeter(), item.getKilogramsPerLinearMeter(), item.getUnitQuantity(), money(item.getUnitPrice()),
                quantity(item.getTotalWeightKg()), money(item.getTotalAmount()), item.getCalculationFormula(), item.isActive(),
                item.getSourceItemId());
    }

    private static String money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String quantity(BigDecimal value) {
        return value == null ? null : value.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }
}
