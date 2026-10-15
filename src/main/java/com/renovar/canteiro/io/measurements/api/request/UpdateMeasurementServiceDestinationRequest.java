package com.renovar.canteiro.io.measurements.api.request;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import com.renovar.canteiro.io.measurements.application.MeasurementContractCompositionAction;
import java.util.UUID;
public record UpdateMeasurementServiceDestinationRequest(@NotNull @PositiveOrZero Integer expectedVersion,
        @NotNull MeasurementContractCompositionAction destinationAction,UUID contractServiceId,@Size(max=1000) String justification){}

