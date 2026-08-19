package com.renovar.canteiro.io.measurements.api.response;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;

import java.util.UUID;

public record MeasurementItemConversionResponse(UUID contractServiceId, UUID changeRequestId,
                                                ChangeAuthorizationMode authorizationMode, boolean alreadyConverted) {
}
