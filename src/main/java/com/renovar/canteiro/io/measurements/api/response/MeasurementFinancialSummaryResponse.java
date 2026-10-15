package com.renovar.canteiro.io.measurements.api.response;

import com.renovar.canteiro.io.measurements.domain.MeasurementFinancialSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/** Canonical financial projection derived by the backend from accepted measurement origins. */
public record MeasurementFinancialSummaryResponse(
        UUID measurementId,
        @Schema(types={"string","null"},format="uuid") UUID contractId,
        @Schema(type = "string", format = "decimal", example = "1200.50") String acceptedAmount,
        @Schema(types={"string","null"}, format = "decimal", example = "800.00") String billedAmount,
        @Schema(types={"string","null"}, format = "decimal", example = "500.00") String receivedAmount,
        @Schema(types={"string","null"}, format = "decimal", example = "400.50") String acceptedAvailableToBill,
        @Schema(types={"string","null"}, format = "decimal", example = "300.00") String billedOutstandingAmount,
        List<MeasurementFinancialServiceResponse> services,
        @Schema(allowableValues={"AVAILABLE","PARTIALLY_COMPOSED","RECONCILIATION_REQUIRED","LEGACY_ORIGIN_REQUIRES_RECONCILIATION","NO_CONTRACT","NOT_AUTHORIZED"}) String financialAvailability,
        java.time.Instant asOf,
        @Schema(types={"string","null"},format="decimal",example="6000.00") String remainingToReceive,
        @Schema(types={"string","null"},format="decimal",example="50.00") String financialAdjustmentAmount,
        @Schema(types={"string","null"},format="decimal",example="100.00") String acceptedUncomposedAmount,
        com.renovar.canteiro.io.shared.domain.CurrencyCode currency
) {
    public static MeasurementFinancialSummaryResponse from(MeasurementFinancialSummary summary) {
        return new MeasurementFinancialSummaryResponse(summary.measurementId(), summary.contractId(),
                money(summary.acceptedAmount()), money(summary.billedAmount()), money(summary.receivedAmount()),
                money(summary.acceptedAvailableToBill()), money(summary.billedOutstandingAmount()),
                summary.services().stream().map(MeasurementFinancialServiceResponse::from).toList(),summary.financialAvailability(),summary.asOf(),
                money(summary.remainingToReceive()),money(summary.financialAdjustmentAmount()),money(summary.acceptedUncomposedAmount()),summary.currency());
    }

    private static String money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
