package com.renovar.canteiro.io.measurements.api.response;
import java.util.UUID;
public record MeasurementAccumulatedItemResponse(MeasurementItemResponse item,UUID continuousServiceId,
        UUID rootItemId,UUID measurementVersionId,int versionNumber,String readReference,String status,String presentationStatus,boolean editable){}

