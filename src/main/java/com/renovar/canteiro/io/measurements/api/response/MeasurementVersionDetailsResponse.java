package com.renovar.canteiro.io.measurements.api.response;

import com.renovar.canteiro.io.measurements.application.MeasurementVersionDetails;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscountType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Detail of one selected version. It does not imply that this is the latest version. */
public record MeasurementVersionDetailsResponse(
        MeasurementResponse measurement,
        MeasurementVersionResponse version,
        MeasurementCorrectionRequestResponse correctionRequest,
        List<MeasurementItemResponse> items,
        List<MeasurementServiceSummaryResponse> services,
        MeasurementDiscountType discountType,
        @Schema(type = "string", format = "decimal", example = "10.0000", pattern = "^-?\\d+\\.\\d{1,4}$") String discountValue,
        @Schema(type = "string", format = "decimal", example = "1536.0000", pattern = "^-?\\d+\\.\\d{4}$") String totalWeightKg,
        @Schema(type = "string", format = "decimal", example = "1250.50", pattern = "^-?\\d+\\.\\d{2}$") String grossAmount,
        @Schema(type = "string", format = "decimal", example = "50.00", pattern = "^-?\\d+\\.\\d{2}$") String discountAmount,
        @Schema(type = "string", format = "decimal", example = "1200.50", pattern = "^-?\\d+\\.\\d{2}$") String netAmount
) {
    public static MeasurementVersionDetailsResponse from(MeasurementVersionDetails details) {
        return new MeasurementVersionDetailsResponse(MeasurementResponse.from(details.measurement()),
                MeasurementVersionResponse.from(details.version()),
                MeasurementCorrectionRequestResponse.from(details.correctionRequestSource()),
                details.items().stream().map(MeasurementItemResponse::from).toList(),
                details.services().stream().map(MeasurementServiceSummaryResponse::from).toList(),
                details.discount() == null ? null : details.discount().getDiscountType(),
                details.discount() == null ? null : decimal(details.discount().getDiscountValue()),
                quantity(details.amounts().totalWeightKg()), money(details.amounts().grossAmount()),
                money(details.amounts().discountAmount()), money(details.amounts().netAmount()));
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static String money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String quantity(BigDecimal value) {
        return value == null ? null : value.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }
}
