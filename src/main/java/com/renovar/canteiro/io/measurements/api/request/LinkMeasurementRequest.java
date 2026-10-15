package com.renovar.canteiro.io.measurements.api.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record LinkMeasurementRequest(
        @NotNull UUID workId,
        UUID contractId,
        @Size(max = 2000) String justification
) {
}
