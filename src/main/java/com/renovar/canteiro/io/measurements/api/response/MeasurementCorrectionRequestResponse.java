package com.renovar.canteiro.io.measurements.api.response;

import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Immutable external-rejection context carried into the corrective draft's detail. */
public record MeasurementCorrectionRequestResponse(
        UUID rejectedVersionId,
        LocalDate externalAcceptanceOn,
        @Schema(description = "Safe operational description of the corrections requested externally.")
        String externalAcceptanceNotes,
        List<UUID> correctionItemIds
) {
    public static MeasurementCorrectionRequestResponse from(MeasurementVersion version) {
        return version == null ? null : new MeasurementCorrectionRequestResponse(version.getId(),
                version.getExternalAcceptanceOn(), version.getExternalAcceptanceNotes(), version.getCorrectionItemIds());
    }
}
