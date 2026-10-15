package com.renovar.canteiro.io.measurements.api.response;

import com.renovar.canteiro.io.measurements.domain.MeasurementService;
import java.util.UUID;

public record MeasurementServiceGroupResponse(UUID id, String serviceTitle, int sortOrder) {
    public static MeasurementServiceGroupResponse from(MeasurementService service) {
        return new MeasurementServiceGroupResponse(service.getId(), service.getServiceTitle(), service.getSortOrder());
    }
}
