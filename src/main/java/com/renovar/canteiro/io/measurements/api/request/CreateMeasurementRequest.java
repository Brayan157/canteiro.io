package com.renovar.canteiro.io.measurements.api.request;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateMeasurementRequest(
        @NotNull UUID workId,
        UUID contractId,
        @Size(max = 100) String reference,
        @Size(max = 1000) String description,
        LocalDate measuredOn,
        @Size(max = 2000) String justification
) {
}
