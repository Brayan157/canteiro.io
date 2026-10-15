package com.renovar.canteiro.io.measurements.api.response;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/** Direct reorders return the entire authoritative active sequence and totals. */
public record MeasurementItemReorderResponse(
        List<MeasurementItemResponse> items,
        MeasurementVersionResponse version,
        @Schema(type = "string", format = "decimal", example = "1536.0000") String totalWeightKg,
        @Schema(type = "string", format = "decimal", example = "4608.00") String grossAmount,
        @Schema(type = "string", format = "decimal", example = "0.00") String discountAmount,
        @Schema(type = "string", format = "decimal", example = "4608.00") String netAmount,
        UUID changeRequestId,
        ChangeAuthorizationMode authorizationMode
) {
    public static MeasurementItemReorderResponse direct(List<MeasurementItemResponse> items,
                                                        MeasurementVersionResponse version,
                                                        BigDecimal totalWeightKg, BigDecimal grossAmount,
                                                        BigDecimal discountAmount, BigDecimal netAmount,
                                                        ChangeAuthorizationMode authorizationMode) {
        return new MeasurementItemReorderResponse(items, version, quantity(totalWeightKg), money(grossAmount),
                money(discountAmount), money(netAmount), null, authorizationMode);
    }

    public static MeasurementItemReorderResponse pending(UUID changeRequestId,
                                                         ChangeAuthorizationMode authorizationMode) {
        return new MeasurementItemReorderResponse(null, null, null, null, null, null, changeRequestId,
                authorizationMode);
    }

    private static String money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String quantity(BigDecimal value) {
        return value == null ? null : value.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }
}
