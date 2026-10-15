package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.measurements.api.response.MeasurementAccumulatedTotalsResponse;
import java.util.UUID;

/** Official documentary subtotals, already reconciled for corrections and header discount. */
public record MeasurementAccumulatedServiceSubtotal(UUID continuousServiceId, String serviceTitle,
        MeasurementAccumulatedTotalsResponse acceptedTotals, MeasurementAccumulatedTotalsResponse pendingTotals) { }
