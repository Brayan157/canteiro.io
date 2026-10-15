package com.renovar.canteiro.io.measurements.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AdvanceMeasurementWorkflowCommand(
        UUID measurementId,
        UUID measurementVersionId,
        MeasurementWorkflowAction action,
        Boolean externallyAccepted,
        LocalDate externalAcceptanceOn,
        String externalAcceptanceNotes,
        List<UUID> correctionItemIds,
        String justification
) {
}
