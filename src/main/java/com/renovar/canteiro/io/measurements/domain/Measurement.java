package com.renovar.canteiro.io.measurements.domain;

import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
public final class Measurement {

    private final UUID id;
    private final UUID companyId;
    private final UUID workId;
    private final UUID contractId;
    private final String reference;
    private final String description;
    private final LocalDate measuredOn;
    private MeasurementStatus status;
    private final int lockVersion;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Measurement(UUID id, UUID companyId, UUID workId, UUID contractId, String reference, String description,
                        LocalDate measuredOn, MeasurementStatus status, int lockVersion, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.companyId = require(companyId, "Measurement company is required");
        this.workId = require(workId, "Measurement work is required");
        this.contractId = contractId;
        this.reference = normalize(reference);
        this.description = normalize(description);
        this.measuredOn = measuredOn;
        this.status = require(status, "Measurement status is required");
        if (lockVersion < 0) {
            throw new IllegalArgumentException("Measurement lock version must not be negative");
        }
        this.lockVersion = lockVersion;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Measurement create(UUID companyId, UUID workId, UUID contractId, String reference,
                                     String description, LocalDate measuredOn) {
        return new Measurement(null, companyId, workId, contractId, reference, description, measuredOn,
                MeasurementStatus.DRAFT, 0, null, null);
    }

    public static Measurement rehydrate(UUID id, UUID companyId, UUID workId, UUID contractId, String reference,
                                        String description, LocalDate measuredOn, MeasurementStatus status, int lockVersion,
                                        Instant createdAt, Instant updatedAt) {
        return new Measurement(id, companyId, workId, contractId, reference, description, measuredOn, status, lockVersion,
                createdAt, updatedAt);
    }

    public void markSent() {
        transition(MeasurementStatus.DRAFT, MeasurementStatus.SENT);
    }

    public void markPendingAcceptance() {
        transition(MeasurementStatus.SENT, MeasurementStatus.PENDING_ACCEPTANCE);
    }

    public void recordExternalAcceptance(boolean accepted) {
        transition(MeasurementStatus.PENDING_ACCEPTANCE, accepted ? MeasurementStatus.ACCEPTED : MeasurementStatus.REJECTED);
    }

    public void finalizeMeasurement() {
        transition(MeasurementStatus.ACCEPTED, MeasurementStatus.FINALIZED);
    }

    public void startRevision() {
        transition(MeasurementStatus.ACCEPTED, MeasurementStatus.DRAFT);
    }

    private void transition(MeasurementStatus expected, MeasurementStatus target) {
        if (status != expected) {
            throw new IllegalStateException("Measurement must be " + expected + " before changing to " + target);
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
