package com.renovar.canteiro.io.measurements.api.response;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;

import java.util.UUID;

public record MeasurementChangeResponse(
        MeasurementResponse measurement,
        MeasurementVersionResponse version,
        UUID changeRequestId,
        ChangeAuthorizationMode authorizationMode
) {
}
