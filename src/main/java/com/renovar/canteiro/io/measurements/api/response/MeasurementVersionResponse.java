package com.renovar.canteiro.io.measurements.api.response;

import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MeasurementVersionResponse(
        UUID id,
        int versionNumber,
        UUID previousVersionId,
        MeasurementVersionStatus status,
        int lockVersion,
        LocalDate externalAcceptanceOn,
        String externalAcceptanceNotes,
        Instant createdAt,
        Instant updatedAt
) {
    public static MeasurementVersionResponse from(MeasurementVersion version) {
        return version == null ? null : new MeasurementVersionResponse(version.getId(), version.getVersionNumber(),
                version.getPreviousVersionId(), version.getStatus(), version.getLockVersion(),
                version.getExternalAcceptanceOn(), version.getExternalAcceptanceNotes(), version.getCreatedAt(),
                version.getUpdatedAt());
    }
}
