package com.renovar.canteiro.io.measurements.application;

import java.util.UUID;

public record ConvertAcceptedMeasurementItemCommand(UUID measurementId, UUID measurementVersionId, UUID measurementItemId,
                                                    String justification) {
}
