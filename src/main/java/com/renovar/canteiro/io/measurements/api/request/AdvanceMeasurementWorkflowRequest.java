package com.renovar.canteiro.io.measurements.api.request;

import com.renovar.canteiro.io.measurements.application.MeasurementWorkflowAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AdvanceMeasurementWorkflowRequest(
        @NotNull MeasurementWorkflowAction action,
        Boolean externallyAccepted,
        LocalDate externalAcceptanceOn,
        @Size(max = 2000) String externalAcceptanceNotes,
        @Size(max = 2000) String justification
) {
}
