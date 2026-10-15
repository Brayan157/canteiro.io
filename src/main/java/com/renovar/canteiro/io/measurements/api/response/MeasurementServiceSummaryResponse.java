package com.renovar.canteiro.io.measurements.api.response;

import com.renovar.canteiro.io.measurements.domain.MeasurementServiceSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Official aggregation; the header discount is deliberately not allocated among services. */
public record MeasurementServiceSummaryResponse(
        String serviceTitle,
        int itemCount,
        @Schema(type = "string", format = "decimal", example = "4608.00", pattern = "^-?\\d+\\.\\d{2}$") String grossAmount,
        @Schema(type = "string", format = "decimal", example = "1536.0000", pattern = "^-?\\d+\\.\\d{4}$",
                nullable = true, description = "Null when this service contains only non-weighted lines.") String totalWeightKg
) {
    public static MeasurementServiceSummaryResponse from(MeasurementServiceSummary summary) {
        return new MeasurementServiceSummaryResponse(summary.serviceTitle(), summary.itemCount(),
                money(summary.grossAmount()), quantity(summary.totalWeightKg()));
    }

    private static String money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String quantity(BigDecimal value) {
        return value == null ? null : value.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }
}
