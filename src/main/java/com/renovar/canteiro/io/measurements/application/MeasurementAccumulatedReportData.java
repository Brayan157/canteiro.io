package com.renovar.canteiro.io.measurements.application;
import com.renovar.canteiro.io.measurements.domain.Measurement;
import com.renovar.canteiro.io.measurements.domain.MeasurementFinancialSummary;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
import com.renovar.canteiro.io.measurements.api.response.MeasurementAccumulatedTotalsResponse;
import java.util.List;
public record MeasurementAccumulatedReportData(Measurement measurement,MeasurementVersion currentVersion,
        MeasurementCompanyBranding branding,MeasurementAccumulatedTotalsResponse acceptedTotals,
        MeasurementAccumulatedTotalsResponse pendingTotals,MeasurementFinancialSummary financialSummary,
        List<MeasurementAccumulatedServiceSubtotal> serviceSubtotals) {
    public MeasurementAccumulatedReportData(Measurement measurement,MeasurementVersion currentVersion,
            MeasurementCompanyBranding branding,MeasurementAccumulatedTotalsResponse acceptedTotals,
            MeasurementAccumulatedTotalsResponse pendingTotals,MeasurementFinancialSummary financialSummary) {
        this(measurement,currentVersion,branding,acceptedTotals,pendingTotals,financialSummary,List.of());
    }
}

