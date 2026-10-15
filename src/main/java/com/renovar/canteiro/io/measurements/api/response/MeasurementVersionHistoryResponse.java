package com.renovar.canteiro.io.measurements.api.response;

import com.renovar.canteiro.io.measurements.application.MeasurementVersionHistory;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Timeline projection. Values are official totals for the represented version only. */
public record MeasurementVersionHistoryResponse(
        UUID id,
        int versionNumber,
        UUID previousVersionId,
        MeasurementVersionStatus status,
        LocalDate externalAcceptanceOn,
        Instant createdAt,
        Instant updatedAt,
        @Schema(type = "string", format = "decimal", example = "1536.0000", pattern = "^-?\\d+\\.\\d{4}$") String totalWeightKg,
        @Schema(type = "string", format = "decimal", example = "4608.00", pattern = "^-?\\d+\\.\\d{2}$") String grossAmount,
        @Schema(type = "string", format = "decimal", example = "50.00", pattern = "^-?\\d+\\.\\d{2}$") String discountAmount,
        @Schema(type = "string", format = "decimal", example = "4558.00", pattern = "^-?\\d+\\.\\d{2}$") String netAmount,
        com.renovar.canteiro.io.shared.domain.CurrencyCode currency
) {
    public static MeasurementVersionHistoryResponse from(MeasurementVersionHistory history) {
        var version = history.version();
        var amounts = history.amounts();
        return new MeasurementVersionHistoryResponse(version.getId(), version.getVersionNumber(), version.getPreviousVersionId(),
                version.getStatus(), version.getExternalAcceptanceOn(), version.getCreatedAt(), version.getUpdatedAt(),
                quantity(amounts.totalWeightKg()), money(amounts.grossAmount()), money(amounts.discountAmount()),
                money(amounts.netAmount()), history.currency());
    }

    private static String money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String quantity(BigDecimal value) {
        return value == null ? null : value.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }
}
