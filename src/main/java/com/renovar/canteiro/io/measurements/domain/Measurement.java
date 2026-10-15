package com.renovar.canteiro.io.measurements.domain;

import lombok.Getter;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
public final class Measurement {

    private final UUID id;
    private final UUID companyId;
    private UUID workId;
    private UUID contractId;
    private final CurrencyCode currency;
    private final String title;
    private final String reference;
    private final String description;
    private final LocalDate measuredOn;
    private MeasurementStatus status;
    private final int lockVersion;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Measurement(UUID id, UUID companyId, UUID workId, UUID contractId, String title, String reference, String description,
                        LocalDate measuredOn, MeasurementStatus status, int lockVersion, Instant createdAt, Instant updatedAt, CurrencyCode currency) {
        this.id = id;
        this.companyId = require(companyId, "Measurement company is required");
        if (workId == null && contractId != null) {
            throw new IllegalArgumentException("Measurement contract requires a work link");
        }
        this.workId = workId;
        this.contractId = contractId;
        this.currency = CurrencyCode.defaultIfAbsent(currency);
        this.title = requireTitle(title);
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

    public static Measurement create(UUID companyId, UUID workId, UUID contractId, String title, String reference,
                                     String description, LocalDate measuredOn) {
        return create(companyId, workId, contractId, title, reference, description, measuredOn, CurrencyCode.BRL);
    }

    public static Measurement create(UUID companyId, UUID workId, UUID contractId, String title, String reference,
                                     String description, LocalDate measuredOn, CurrencyCode currency) {
        return new Measurement(null, companyId, workId, contractId, title, reference, description, measuredOn,
                MeasurementStatus.DRAFT, 0, null, null, currency);
    }

    /** Compatibility factory for existing internal callers. New API requests must provide title. */
    @Deprecated(forRemoval = false)
    public static Measurement create(UUID companyId, UUID workId, UUID contractId, String reference,
                                     String description, LocalDate measuredOn) {
        return create(companyId, workId, contractId, legacyTitle(reference, description), reference, description, measuredOn);
    }

    public static Measurement rehydrate(UUID id, UUID companyId, UUID workId, UUID contractId, String title, String reference,
                                        String description, LocalDate measuredOn, MeasurementStatus status, int lockVersion,
                                        Instant createdAt, Instant updatedAt) {
        return rehydrate(id, companyId, workId, contractId, title, reference, description, measuredOn, status, lockVersion,
                createdAt, updatedAt, CurrencyCode.BRL);
    }

    public static Measurement rehydrate(UUID id, UUID companyId, UUID workId, UUID contractId, String title, String reference,
                                        String description, LocalDate measuredOn, MeasurementStatus status, int lockVersion,
                                        Instant createdAt, Instant updatedAt, CurrencyCode currency) {
        return new Measurement(id, companyId, workId, contractId, title, reference, description, measuredOn, status, lockVersion,
                createdAt, updatedAt, currency);
    }

    /** Compatibility factory for measurements persisted before the title was introduced. */
    @Deprecated(forRemoval = false)
    public static Measurement rehydrate(UUID id, UUID companyId, UUID workId, UUID contractId, String reference,
                                        String description, LocalDate measuredOn, MeasurementStatus status, int lockVersion,
                                        Instant createdAt, Instant updatedAt) {
        return rehydrate(id, companyId, workId, contractId, legacyTitle(reference, description), reference, description,
                measuredOn, status, lockVersion, createdAt, updatedAt);
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
        if (workId == null || contractId == null) {
            throw new IllegalStateException("Measurement must be linked to a work and contract before finalization");
        }
        transition(MeasurementStatus.ACCEPTED, MeasurementStatus.FINALIZED);
    }

    public void linkToWork(UUID workId, UUID contractId) {
        UUID requiredWorkId = require(workId, "Measurement work is required for linking");
        if (status == MeasurementStatus.FINALIZED || status == MeasurementStatus.CANCELLED) {
            throw new IllegalStateException("Finalized or cancelled measurement cannot change its work link");
        }
        if (this.workId != null && !this.workId.equals(requiredWorkId)) {
            throw new IllegalStateException("Measurement work link cannot be changed");
        }
        if (this.contractId != null && contractId != null && !this.contractId.equals(contractId)) {
            throw new IllegalStateException("Measurement contract link cannot be changed");
        }
        this.workId = requiredWorkId;
        if (contractId != null) {
            this.contractId = contractId;
        }
    }

    public void cancel() {
        if (contractId != null) {
            throw new IllegalStateException("Measurement linked to a contract cannot be cancelled");
        }
        status = MeasurementStatus.CANCELLED;
    }

    public void startRevision() {
        if (status != MeasurementStatus.ACCEPTED && status != MeasurementStatus.REJECTED) {
            throw new IllegalStateException("Measurement must be accepted or rejected before starting a revision");
        }
        status = MeasurementStatus.DRAFT;
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

    private static String requireTitle(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException("Measurement title is required");
        }
        if (normalized.length() > 200) {
            throw new IllegalArgumentException("Measurement title must not exceed 200 characters");
        }
        return normalized;
    }

    private static String legacyTitle(String reference, String description) {
        String candidate = normalize(reference);
        if (candidate == null) candidate = normalize(description);
        return candidate == null ? "Medição sem título" : candidate.length() <= 200 ? candidate : candidate.substring(0, 200);
    }

    private static <T> T require(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
