package com.renovar.canteiro.io.measurements.api.request;

import com.renovar.canteiro.io.measurements.application.MeasurementWorkflowAction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AdvanceMeasurementWorkflowRequest(
        @NotNull MeasurementWorkflowAction action,
        Boolean externallyAccepted,
        LocalDate externalAcceptanceOn,
        @Schema(description = "Required and nonblank when RECORD_EXTERNAL_ACCEPTANCE records externallyAccepted=false.")
        @Size(max = 2000) String externalAcceptanceNotes,
        @Schema(description = "Optional active item identifiers from this version when an external rejection requests corrections.")
        List<UUID> correctionItemIds,
        @Size(max = 2000) String justification
) {
}
