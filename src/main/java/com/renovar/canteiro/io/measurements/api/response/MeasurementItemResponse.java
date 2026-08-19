package com.renovar.canteiro.io.measurements.api.response;

import com.renovar.canteiro.io.measurements.domain.MeasurementChargeType;
import com.renovar.canteiro.io.measurements.domain.MeasurementItem;

import java.math.BigDecimal;
import java.util.UUID;

public record MeasurementItemResponse(
        UUID id,
        int itemNumber,
        String activity,
        String description,
        MeasurementChargeType chargeType,
        BigDecimal areaSquareMeters,
        BigDecimal linearMeters,
        BigDecimal kilogramsPerSquareMeter,
        BigDecimal kilogramsPerLinearMeter,
        BigDecimal unitPrice,
        BigDecimal totalWeightKg,
        BigDecimal totalAmount,
        String calculationFormula
) {
    public static MeasurementItemResponse from(MeasurementItem item) {
        return item == null ? null : new MeasurementItemResponse(item.getId(), item.getItemNumber(), item.getActivity(),
                item.getDescription(), item.getChargeType(), item.getAreaSquareMeters(), item.getLinearMeters(),
                item.getKilogramsPerSquareMeter(), item.getKilogramsPerLinearMeter(), item.getUnitPrice(),
                item.getTotalWeightKg(), item.getTotalAmount(), item.getCalculationFormula());
    }
}
