package com.renovar.canteiro.io.measurements.api.response;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;

import java.util.List;
import java.util.UUID;

public record MeasurementRevisionResponse(
        MeasurementResponse measurement,
        MeasurementVersionResponse version,
        List<MeasurementItemResponse> items,
        UUID changeRequestId,
        ChangeAuthorizationMode authorizationMode
) {
}
