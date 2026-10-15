package com.renovar.canteiro.io.measurements.api.response;
import com.renovar.canteiro.io.measurements.domain.MeasurementCompositionState;
import java.util.List;
public record MeasurementAccumulatedServiceResponse(MeasurementContinuousServiceResponse service,
        List<MeasurementAccumulatedItemResponse> items,MeasurementAccumulatedTotalsResponse acceptedTotals,
        MeasurementAccumulatedTotalsResponse pendingTotals,List<MeasurementCompositionState> compositions){}

