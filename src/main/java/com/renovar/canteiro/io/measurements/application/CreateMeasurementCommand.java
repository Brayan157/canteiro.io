package com.renovar.canteiro.io.measurements.application;

import java.time.LocalDate;
import java.util.UUID;

public record CreateMeasurementCommand(
        UUID workId,
        UUID contractId,
        String reference,
        String description,
        LocalDate measuredOn,
        String justification
) {
}
