package com.renovar.canteiro.io.measurements.api.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.renovar.canteiro.io.measurements.application.MeasurementContractCompositionAction;
import java.util.UUID;
public record CreateMeasurementContinuousServiceRequest(@NotBlank @Size(max=255) String serviceTitle,
        MeasurementContractCompositionAction destinationAction,UUID contractServiceId,@Size(max=1000) String justification){}

