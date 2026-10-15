package com.renovar.canteiro.io.measurements.api.response;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/** Direct corrections return the authoritative version totals. A 202 has only its request identifier. */
public record MeasurementItemCorrectionResponse(
        MeasurementItemResponse item,
        MeasurementVersionResponse version,
        @Schema(type = "string", format = "decimal", example = "1536.0000") String totalWeightKg,
        @Schema(type = "string", format = "decimal", example = "4608.00") String grossAmount,
        @Schema(type = "string", format = "decimal", example = "0.00") String discountAmount,
        @Schema(type = "string", format = "decimal", example = "4608.00") String netAmount,
        UUID changeRequestId,
        ChangeAuthorizationMode authorizationMode
) {
    public static MeasurementItemCorrectionResponse direct(
            MeasurementItemResponse item, MeasurementVersionResponse version,
            BigDecimal totalWeightKg, BigDecimal grossAmount, BigDecimal discountAmount, BigDecimal netAmount,
            ChangeAuthorizationMode authorizationMode) {
        return new MeasurementItemCorrectionResponse(item, version, quantity(totalWeightKg), money(grossAmount),
                money(discountAmount), money(netAmount), null, authorizationMode);
    }

    public static MeasurementItemCorrectionResponse pending(UUID changeRequestId, ChangeAuthorizationMode authorizationMode) {
        return new MeasurementItemCorrectionResponse(null, null, null, null, null, null, changeRequestId, authorizationMode);
    }

    private static String money(BigDecimal value) { return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString(); }
    private static String quantity(BigDecimal value) { return value == null ? null : value.setScale(4, RoundingMode.HALF_UP).toPlainString(); }
}
