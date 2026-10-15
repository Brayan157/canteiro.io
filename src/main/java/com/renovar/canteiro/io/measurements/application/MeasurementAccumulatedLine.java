package com.renovar.canteiro.io.measurements.application;
import com.renovar.canteiro.io.measurements.domain.MeasurementItem;
import com.renovar.canteiro.io.measurements.domain.MeasurementContinuousService;
import java.util.UUID;
public record MeasurementAccumulatedLine(UUID continuousServiceId,String serviceTitle,MeasurementItem item,
        int versionNumber,String status,String presentationStatus) {
    /** A boundary also represents intentionally empty services; it is not an item. */
    public static MeasurementAccumulatedLine serviceHeader(MeasurementContinuousService service) {
        return new MeasurementAccumulatedLine(service.id(),service.serviceTitle(),null,0,"SERVICE_HEADER",null);
    }
}

