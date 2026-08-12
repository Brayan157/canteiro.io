package com.renovar.canteiro.io.governance.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class ChangeRequest {

    private final UUID id;
    private final UUID companyId;
    private final UUID requesterUserId;
    private final AuditModule module;
    private final ChangeRequestOperation operation;
    private final String entityType;
    private final UUID entityId;
    private final long entityVersion;
    private final int revision;
    private ChangeRequestStatus status;
    private final ChangeRequestSnapshot snapshot;
    private final String justification;
    private UUID decidedByUserId;
    private String decisionReason;
    private Instant decidedAt;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    private ChangeRequest(
            UUID id,
            UUID companyId,
            UUID requesterUserId,
            AuditModule module,
            ChangeRequestOperation operation,
            String entityType,
            UUID entityId,
            long entityVersion,
            int revision,
            ChangeRequestStatus status,
            ChangeRequestSnapshot snapshot,
            String justification,
            UUID decidedByUserId,
            String decisionReason,
            Instant decidedAt,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.companyId = require(companyId, "Company id");
        this.requesterUserId = require(requesterUserId, "Requester user id");
        this.module = require(module, "Module");
        this.operation = require(operation, "Operation");
        this.entityType = requireEntityType(entityType);
        this.entityId = requireEntityId(operation, entityId);
        this.entityVersion = requireNonNegative(entityVersion, "Entity version");
        this.revision = requireRevision(revision);
        this.status = require(status, "Status");
        this.snapshot = require(snapshot, "Snapshot");
        this.justification = normalizeJustification(justification);
        this.decidedByUserId = decidedByUserId;
        this.decisionReason = normalizeDecisionReason(decisionReason);
        this.decidedAt = decidedAt;
        validateDecisionFields();
        this.version = requireNonNegative(version, "Version");
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ChangeRequest create(
            UUID companyId,
            UUID requesterUserId,
            AuditModule module,
            ChangeRequestOperation operation,
            String entityType,
            UUID entityId,
            long entityVersion,
            ChangeRequestSnapshot snapshot,
            String justification
    ) {
        return new ChangeRequest(
                null,
                companyId,
                requesterUserId,
                module,
                operation,
                entityType,
                entityId,
                entityVersion,
                1,
                ChangeRequestStatus.PENDING,
                snapshot,
                justification,
                null,
                null,
                null,
                0,
                null,
                null
        );
    }

    public static ChangeRequest rehydrate(
            UUID id,
            UUID companyId,
            UUID requesterUserId,
            AuditModule module,
            ChangeRequestOperation operation,
            String entityType,
            UUID entityId,
            long entityVersion,
            int revision,
            ChangeRequestStatus status,
            ChangeRequestSnapshot snapshot,
            String justification,
            UUID decidedByUserId,
            String decisionReason,
            Instant decidedAt,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new ChangeRequest(
                require(id, "Change request id"),
                companyId,
                requesterUserId,
                module,
                operation,
                entityType,
                entityId,
                entityVersion,
                revision,
                status,
                snapshot,
                justification,
                decidedByUserId,
                decisionReason,
                decidedAt,
                version,
                createdAt,
                updatedAt
        );
    }

    public void approve(UUID approverUserId, Instant decidedAt, String decisionReason) {
        requirePending();
        UUID approver = require(approverUserId, "Approver user id");
        String normalizedDecisionReason = normalizeDecisionReason(decisionReason);
        Instant decisionTimestamp = require(decidedAt, "Decision timestamp");
        this.status = ChangeRequestStatus.APPROVED;
        this.decidedByUserId = approver;
        this.decisionReason = normalizedDecisionReason;
        this.decidedAt = decisionTimestamp;
    }

    public void reject(UUID approverUserId, Instant decidedAt, String decisionReason) {
        requirePending();
        UUID approver = require(approverUserId, "Approver user id");
        String normalizedDecisionReason = requireDecisionReason(decisionReason);
        Instant decisionTimestamp = require(decidedAt, "Decision timestamp");
        this.status = ChangeRequestStatus.REJECTED;
        this.decidedByUserId = approver;
        this.decisionReason = normalizedDecisionReason;
        this.decidedAt = decisionTimestamp;
    }

    private static String requireEntityType(String value) {
        if (value == null || value.isBlank() || value.length() > 100) {
            throw new IllegalArgumentException("Entity type must have between 1 and 100 characters");
        }
        return value;
    }

    private static UUID requireEntityId(ChangeRequestOperation operation, UUID entityId) {
        if (operation != ChangeRequestOperation.CREATE && entityId == null) {
            throw new IllegalArgumentException("Entity id is required for an existing entity change request");
        }
        return entityId;
    }

    private static String normalizeJustification(String value) {
        if (value == null) {
            return null;
        }
        String normalizedValue = value.trim();
        if (normalizedValue.isEmpty() || normalizedValue.length() > 1000) {
            throw new IllegalArgumentException("Justification must have between 1 and 1000 characters when provided");
        }
        return normalizedValue;
    }

    private static String normalizeDecisionReason(String value) {
        if (value == null) {
            return null;
        }
        String normalizedValue = value.trim();
        if (normalizedValue.isEmpty() || normalizedValue.length() > 1000) {
            throw new IllegalArgumentException("Decision reason must have between 1 and 1000 characters when provided");
        }
        return normalizedValue;
    }

    private static String requireDecisionReason(String value) {
        String normalizedValue = normalizeDecisionReason(value);
        if (normalizedValue == null) {
            throw new IllegalArgumentException("Decision reason is required when rejecting a change request");
        }
        return normalizedValue;
    }

    private void requirePending() {
        if (status != ChangeRequestStatus.PENDING) {
            throw new IllegalStateException("Only pending change requests can be decided");
        }
    }

    private void validateDecisionFields() {
        if (status == ChangeRequestStatus.PENDING && (decidedByUserId != null || decisionReason != null || decidedAt != null)) {
            throw new IllegalArgumentException("Pending change requests cannot have a decision");
        }
        if (status == ChangeRequestStatus.APPROVED && (decidedByUserId == null || decidedAt == null)) {
            throw new IllegalArgumentException("Approved change requests require a decision actor and timestamp");
        }
        if (status == ChangeRequestStatus.REJECTED
                && (decidedByUserId == null || decisionReason == null || decidedAt == null)) {
            throw new IllegalArgumentException("Rejected change requests require a decision actor, reason and timestamp");
        }
    }

    private static int requireRevision(int value) {
        if (value < 1) {
            throw new IllegalArgumentException("Revision must be at least one");
        }
        return value;
    }

    private static long requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
