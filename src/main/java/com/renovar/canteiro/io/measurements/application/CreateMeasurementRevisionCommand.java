package com.renovar.canteiro.io.measurements.application;

import java.util.UUID;

public record CreateMeasurementRevisionCommand(UUID measurementId, String justification) {
}
