package com.renovar.canteiro.io.measurements.api.response;

import com.renovar.canteiro.io.measurements.domain.MeasurementCompositionFinancialPosition;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/** Financial trace of one stable accepted measurement service group. */
public record MeasurementFinancialServiceResponse(
        @Schema(types={"string","null"},format="uuid") UUID measurementContractCompositionId,
        @Schema(types={"string","null"},format="uuid") UUID measurementVersionId,
        @Schema(types={"string","null"},format="uuid") UUID measurementServiceId,
        String serviceTitle,
        @Schema(types={"string","null"},format="uuid") UUID contractServiceId,
        @Schema(type = "string", format = "decimal", example = "1200.50") String acceptedAmount,
        @Schema(type = "string", format = "decimal", example = "800.00") String billedAmount,
        @Schema(type = "string", format = "decimal", example = "500.00") String receivedAmount,
        @Schema(type = "string", format = "decimal", example = "400.50") String acceptedAvailableToBill,
        @Schema(type = "string", format = "decimal", example = "300.00") String billedOutstandingAmount,
        UUID continuousServiceId,
        java.util.List<UUID> compositionIds,
        @Schema(type="string",format="decimal",example="6000.00") String remainingToReceive,
        @Schema(type="string",format="decimal",example="50.00") String financialAdjustmentAmount
) {
    public static MeasurementFinancialServiceResponse from(MeasurementCompositionFinancialPosition position) {
        return new MeasurementFinancialServiceResponse(position.measurementContractCompositionId(),
                position.measurementVersionId(), position.measurementServiceId(), position.serviceTitle(),
                position.contractServiceId(), money(position.acceptedAmount()), money(position.billedAmount()),
                money(position.receivedAmount()), money(position.acceptedAvailableToBill()),
                money(position.billedOutstandingAmount()),position.continuousServiceId(),position.compositionIds(),
                money(position.remainingToReceive()),money(position.financialAdjustmentAmount()));
    }

    private static String money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
