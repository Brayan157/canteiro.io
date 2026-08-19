package com.renovar.canteiro.io.measurements.application;

import java.time.LocalDate;
import java.util.UUID;

public record AdvanceMeasurementWorkflowCommand(
        UUID measurementId,
        UUID measurementVersionId,
        MeasurementWorkflowAction action,
        Boolean externallyAccepted,
        LocalDate externalAcceptanceOn,
        String externalAcceptanceNotes,
        String justification
) {
}
