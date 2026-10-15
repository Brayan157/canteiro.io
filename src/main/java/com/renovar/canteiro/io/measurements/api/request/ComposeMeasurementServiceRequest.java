package com.renovar.canteiro.io.measurements.api.request;

import com.renovar.canteiro.io.measurements.application.MeasurementContractCompositionAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ComposeMeasurementServiceRequest(
        @NotNull MeasurementContractCompositionAction action,
        @NotNull UUID contractId,
        UUID contractServiceId,
        @Size(max = 2000) String justification
) {
}
