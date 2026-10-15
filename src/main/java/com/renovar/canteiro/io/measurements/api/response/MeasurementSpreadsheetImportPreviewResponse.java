package com.renovar.canteiro.io.measurements.api.response;

import com.renovar.canteiro.io.measurements.application.MeasurementSpreadsheetImportPreview;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

public record MeasurementSpreadsheetImportPreviewResponse(
        UUID measurementId,
        UUID measurementVersionId,
        String originalFilename,
        @Schema(description = "SHA-256 returned by preview and required unchanged on confirmation.") String spreadsheetHash,
        boolean valid,
        List<MeasurementSpreadsheetImportDifferenceResponse> differences,
        List<MeasurementSpreadsheetImportLineErrorResponse> lineErrors,
        @Schema(type = "string", format = "decimal", example = "1536.0000") String totalWeightKg,
        @Schema(type = "string", format = "decimal", example = "4608.00") String grossAmount,
        @Schema(type = "string", format = "decimal", example = "0.00") String discountAmount,
        @Schema(type = "string", format = "decimal", example = "4608.00") String netAmount
) {
    public static MeasurementSpreadsheetImportPreviewResponse from(MeasurementSpreadsheetImportPreview preview) {
        return new MeasurementSpreadsheetImportPreviewResponse(preview.measurementId(), preview.measurementVersionId(),
                preview.originalFilename(), preview.spreadsheetHash(), preview.valid(),
                preview.differences().stream().map(value -> new MeasurementSpreadsheetImportDifferenceResponse(
                        value.spreadsheetRow(), value.itemId(), value.type(), value.serviceTitle(), value.activity())).toList(),
                preview.lineErrors().stream().map(value -> new MeasurementSpreadsheetImportLineErrorResponse(
                        value.spreadsheetRow(), value.field(), value.message())).toList(),
                quantity(preview.amounts().totalWeightKg()), money(preview.amounts().grossAmount()),
                money(preview.amounts().discountAmount()), money(preview.amounts().netAmount()));
    }

    private static String money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP).toPlainString(); }
    private static String quantity(BigDecimal value) { return value.setScale(4, RoundingMode.HALF_UP).toPlainString(); }
}
