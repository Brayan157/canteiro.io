package com.renovar.canteiro.io.measurements.domain;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public record MeasurementFinancialSummary(UUID measurementId,UUID contractId,BigDecimal acceptedAmount,
        BigDecimal billedAmount,BigDecimal receivedAmount,BigDecimal acceptedAvailableToBill,BigDecimal billedOutstandingAmount,
        List<MeasurementCompositionFinancialPosition> services,String financialAvailability,Instant asOf,
        BigDecimal remainingToReceive,BigDecimal financialAdjustmentAmount,BigDecimal acceptedUncomposedAmount,
        com.renovar.canteiro.io.shared.domain.CurrencyCode currency) {
    public MeasurementFinancialSummary { services=List.copyOf(services); }
    public MeasurementFinancialSummary(UUID measurementId,UUID contractId,BigDecimal acceptedAmount,
            BigDecimal billedAmount,BigDecimal receivedAmount,BigDecimal acceptedAvailableToBill,BigDecimal billedOutstandingAmount,
            List<MeasurementCompositionFinancialPosition> services,String financialAvailability,Instant asOf,
            BigDecimal remainingToReceive,BigDecimal financialAdjustmentAmount,BigDecimal acceptedUncomposedAmount) {
        this(measurementId,contractId,acceptedAmount,billedAmount,receivedAmount,acceptedAvailableToBill,billedOutstandingAmount,
                services,financialAvailability,asOf,remainingToReceive,financialAdjustmentAmount,acceptedUncomposedAmount,
                com.renovar.canteiro.io.shared.domain.CurrencyCode.BRL);
    }
}
