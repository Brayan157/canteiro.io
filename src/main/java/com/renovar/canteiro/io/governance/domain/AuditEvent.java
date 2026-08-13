package com.renovar.canteiro.io.governance.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class AuditEvent {

    private final UUID id;
    private final UUID companyId;
    private final UUID actorUserId;
    private final AuditActorType actorType;
    private final AuditModule module;
    private final AuditAction action;
    private final String entityType;
    private final UUID entityId;
    private final AuditPayload beforeData;
    private final AuditPayload afterData;
    private final AuditPayload metadata;
    private final Instant occurredAt;
    private final Instant createdAt;

    private AuditEvent(
            UUID id,
            UUID companyId,
            UUID actorUserId,
            AuditActorType actorType,
            AuditModule module,
            AuditAction action,
            String entityType,
            UUID entityId,
            AuditPayload beforeData,
            AuditPayload afterData,
            AuditPayload metadata,
            Instant occurredAt,
            Instant createdAt
    ) {
        this.id = id;
        this.companyId = companyId;
        this.actorType = require(actorType, "Actor type");
        this.actorUserId = requireActorUserId(actorUserId, actorType);
        this.module = require(module, "Module");
        this.action = require(action, "Action");
        this.entityType = requireEntityType(entityType);
        this.entityId = entityId;
        this.beforeData = beforeData;
        this.afterData = afterData;
        this.metadata = metadata == null ? AuditPayload.empty() : metadata;
        this.occurredAt = require(occurredAt, "Occurred at");
        this.createdAt = createdAt;
    }

    public static AuditEvent create(
            UUID companyId,
            UUID actorUserId,
            AuditActorType actorType,
            AuditModule module,
            AuditAction action,
            String entityType,
            UUID entityId,
            AuditPayload beforeData,
            AuditPayload afterData,
            AuditPayload metadata,
            Instant occurredAt
    ) {
        return new AuditEvent(
                null,
                companyId,
                actorUserId,
                actorType,
                module,
                action,
                entityType,
                entityId,
                beforeData,
                afterData,
                metadata,
                occurredAt,
                null
        );
    }

    public static AuditEvent rehydrate(
            UUID id,
            UUID companyId,
            UUID actorUserId,
            AuditActorType actorType,
            AuditModule module,
            AuditAction action,
            String entityType,
            UUID entityId,
            AuditPayload beforeData,
            AuditPayload afterData,
            AuditPayload metadata,
            Instant occurredAt,
            Instant createdAt
    ) {
        return new AuditEvent(
                require(id, "Audit event id"),
                companyId,
                actorUserId,
                actorType,
                module,
                action,
                entityType,
                entityId,
                beforeData,
                afterData,
                metadata,
                occurredAt,
                createdAt
        );
    }

    private static String requireEntityType(String value) {
        if (value == null || value.isBlank() || value.length() > 100) {
            throw new IllegalArgumentException("Entity type must have between 1 and 100 characters");
        }
        return value;
    }

    private static UUID requireActorUserId(UUID actorUserId, AuditActorType actorType) {
        if (actorType == AuditActorType.SYSTEM) {
            if (actorUserId != null) {
                throw new IllegalArgumentException("System audit actor cannot identify a user");
            }
            return null;
        }
        return require(actorUserId, "Actor user id");
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
