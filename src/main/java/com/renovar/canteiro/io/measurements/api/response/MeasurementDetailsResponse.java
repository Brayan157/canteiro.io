package com.renovar.canteiro.io.measurements.api.response;

import com.renovar.canteiro.io.measurements.application.MeasurementDetails;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscountType;

import java.math.BigDecimal;
import java.util.List;

public record MeasurementDetailsResponse(
        MeasurementResponse measurement,
        MeasurementVersionResponse latestVersion,
        List<MeasurementItemResponse> items,
        MeasurementDiscountType discountType,
        BigDecimal discountValue,
        BigDecimal grossAmount,
        BigDecimal discountAmount,
        BigDecimal netAmount
) {
    public static MeasurementDetailsResponse from(MeasurementDetails details) {
        return new MeasurementDetailsResponse(MeasurementResponse.from(details.measurement()),
                MeasurementVersionResponse.from(details.latestVersion()),
                details.items().stream().map(MeasurementItemResponse::from).toList(),
                details.discount() == null ? null : details.discount().getDiscountType(),
                details.discount() == null ? null : details.discount().getDiscountValue(),
                details.amounts().grossAmount(), details.amounts().discountAmount(), details.amounts().netAmount());
    }
}
