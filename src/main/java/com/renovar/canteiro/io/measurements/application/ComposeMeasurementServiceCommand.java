package com.renovar.canteiro.io.measurements.application;

import java.util.UUID;

public record ComposeMeasurementServiceCommand(UUID measurementId, UUID measurementVersionId, UUID measurementServiceId,
                                               MeasurementContractCompositionAction action, UUID contractId,
                                               UUID contractServiceId, String justification) {
}
