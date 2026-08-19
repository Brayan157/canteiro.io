package com.renovar.canteiro.io.measurements.domain;

import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
public final class MeasurementVersion {

    private final UUID id;
    private final UUID companyId;
    private final UUID measurementId;
    private final int versionNumber;
    private final UUID previousVersionId;
    private MeasurementVersionStatus status;
    private final int lockVersion;
    private LocalDate externalAcceptanceOn;
    private String externalAcceptanceNotes;
    private final Instant createdAt;
    private final Instant updatedAt;

    private MeasurementVersion(UUID id, UUID companyId, UUID measurementId, int versionNumber,
                               UUID previousVersionId, MeasurementVersionStatus status, int lockVersion, LocalDate externalAcceptanceOn,
                               String externalAcceptanceNotes, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.companyId = require(companyId, "Measurement version company is required");
        this.measurementId = require(measurementId, "Measurement version measurement is required");
        if (versionNumber < 1) {
            throw new IllegalArgumentException("Measurement version number must be positive");
        }
        this.versionNumber = versionNumber;
        this.previousVersionId = previousVersionId;
        this.status = require(status, "Measurement version status is required");
        if (lockVersion < 0) {
            throw new IllegalArgumentException("Measurement version lock version must not be negative");
        }
        this.lockVersion = lockVersion;
        this.externalAcceptanceOn = externalAcceptanceOn;
        this.externalAcceptanceNotes = normalize(externalAcceptanceNotes);
        if ((status == MeasurementVersionStatus.ACCEPTED || status == MeasurementVersionStatus.REJECTED)
                && externalAcceptanceOn == null) {
            throw new IllegalArgumentException("An external acceptance date is required for a decided measurement version");
        }
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static MeasurementVersion create(UUID companyId, UUID measurementId, int versionNumber) {
        return new MeasurementVersion(null, companyId, measurementId, versionNumber, null, MeasurementVersionStatus.DRAFT,
                0, null, null, null, null);
    }

    public static MeasurementVersion createRevision(UUID companyId, UUID measurementId, int versionNumber,
                                                    UUID previousVersionId) {
        return new MeasurementVersion(null, companyId, measurementId, versionNumber, require(previousVersionId,
                "Measurement revision previous version is required"), MeasurementVersionStatus.DRAFT, 0, null, null,
                null, null);
    }

    public static MeasurementVersion rehydrate(UUID id, UUID companyId, UUID measurementId, int versionNumber,
                                               UUID previousVersionId, MeasurementVersionStatus status, int lockVersion,
                                               LocalDate externalAcceptanceOn, String externalAcceptanceNotes,
                                               Instant createdAt, Instant updatedAt) {
        return new MeasurementVersion(id, companyId, measurementId, versionNumber, previousVersionId, status, lockVersion,
                externalAcceptanceOn, externalAcceptanceNotes, createdAt, updatedAt);
    }

    public void markSent() {
        transition(MeasurementVersionStatus.DRAFT, MeasurementVersionStatus.SENT);
    }

    public void markPendingAcceptance() {
        transition(MeasurementVersionStatus.SENT, MeasurementVersionStatus.PENDING_ACCEPTANCE);
    }

    public void recordExternalAcceptance(boolean accepted, LocalDate acceptedOn, String notes) {
        transition(MeasurementVersionStatus.PENDING_ACCEPTANCE,
                accepted ? MeasurementVersionStatus.ACCEPTED : MeasurementVersionStatus.REJECTED);
        this.externalAcceptanceOn = require(acceptedOn, "Measurement version external acceptance date is required");
        this.externalAcceptanceNotes = normalize(notes);
    }

    private void transition(MeasurementVersionStatus expected, MeasurementVersionStatus target) {
        if (status != expected) {
            throw new IllegalStateException("Measurement version must be " + expected + " before changing to " + target);
        }
        status = target;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static <T> T require(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
