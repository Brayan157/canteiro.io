package com.renovar.canteiro.io.measurements.domain;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
public record MeasurementCompositionFinancialPosition(UUID measurementContractCompositionId,UUID measurementVersionId,
        UUID measurementServiceId,String serviceTitle,UUID contractServiceId,BigDecimal acceptedAmount,BigDecimal billedAmount,
        BigDecimal receivedAmount,BigDecimal acceptedAvailableToBill,BigDecimal billedOutstandingAmount,
        UUID continuousServiceId,List<UUID> compositionIds,BigDecimal remainingToReceive,BigDecimal financialAdjustmentAmount) {
    public MeasurementCompositionFinancialPosition { compositionIds=List.copyOf(compositionIds); }
}
