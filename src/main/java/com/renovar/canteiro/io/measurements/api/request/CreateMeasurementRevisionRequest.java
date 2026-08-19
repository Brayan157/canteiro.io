package com.renovar.canteiro.io.measurements.api.request;

import jakarta.validation.constraints.Size;

public record CreateMeasurementRevisionRequest(@Size(max = 2000) String justification) {
}
