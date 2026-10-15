package com.renovar.canteiro.io.measurements.application;

import java.util.UUID;

public record LinkMeasurementCommand(
        UUID measurementId,
        UUID workId,
        UUID contractId,
        String justification
) {
}
