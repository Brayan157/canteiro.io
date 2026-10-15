package com.renovar.canteiro.io.measurements.api.response;
import java.util.List;
import java.time.Instant;
public record MeasurementAccumulatedResponse(MeasurementResponse measurement,MeasurementVersionResponse currentVersion,
        List<MeasurementAccumulatedServiceResponse> services,MeasurementAccumulatedTotalsResponse acceptedTotals,
        MeasurementAccumulatedTotalsResponse pendingTotals,MeasurementAccumulatedTotalsResponse accumulatedTotals,
        MeasurementFinancialSummaryResponse financialSummary,Instant asOf){}

