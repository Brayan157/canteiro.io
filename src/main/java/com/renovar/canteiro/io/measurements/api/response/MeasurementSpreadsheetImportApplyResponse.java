package com.renovar.canteiro.io.measurements.api.response;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

public record MeasurementSpreadsheetImportApplyResponse(
        List<MeasurementItemResponse> items,
        MeasurementVersionResponse version,
        @Schema(type = "string", format = "decimal", example = "1536.0000") String totalWeightKg,
        @Schema(type = "string", format = "decimal", example = "4608.00") String grossAmount,
        @Schema(type = "string", format = "decimal", example = "0.00") String discountAmount,
        @Schema(type = "string", format = "decimal", example = "4608.00") String netAmount,
        UUID changeRequestId,
        ChangeAuthorizationMode authorizationMode
) {
    public static MeasurementSpreadsheetImportApplyResponse direct(List<MeasurementItemResponse> items,
                                                                    MeasurementVersionResponse version,
                                                                    BigDecimal totalWeightKg, BigDecimal grossAmount,
                                                                    BigDecimal discountAmount, BigDecimal netAmount,
                                                                    ChangeAuthorizationMode mode) {
        return new MeasurementSpreadsheetImportApplyResponse(items, version, quantity(totalWeightKg), money(grossAmount),
                money(discountAmount), money(netAmount), null, mode);
    }

    public static MeasurementSpreadsheetImportApplyResponse pending(UUID changeRequestId, ChangeAuthorizationMode mode) {
        return new MeasurementSpreadsheetImportApplyResponse(null, null, null, null, null, null, changeRequestId, mode);
    }

    private static String money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP).toPlainString(); }
    private static String quantity(BigDecimal value) { return value.setScale(4, RoundingMode.HALF_UP).toPlainString(); }
}
