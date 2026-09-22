package com.renovar.canteiro.io.governance.infrastructure.persistence;

import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditActorType;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Entity
@Table(name = "audit_event")
@Immutable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEventJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false)
    private AuditActorType actorType;

    @Enumerated(EnumType.STRING)
    @Column(name = "module", nullable = false)
    private AuditModule module;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private AuditAction action;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_data", columnDefinition = "jsonb")
    private Map<String, Object> beforeData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_data", columnDefinition = "jsonb")
    private Map<String, Object> afterData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata;
    @Column(name = "actor_display_name") private String actorDisplayName;
    @Column(name = "entity_display_name") private String entityDisplayName;
    @Column(name = "entity_display_reference") private String entityDisplayReference;
    @Column(name = "event_summary") private String eventSummary;
    @Column(name = "origin") private String origin;
    @Column(name = "support_context") private String supportContext;
    @Column(name = "correlation_id") private String correlationId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public AuditEventJpaEntity(
            UUID companyId,
            UUID actorUserId,
            AuditActorType actorType,
            AuditModule module,
            AuditAction action,
            String entityType,
            UUID entityId,
            Map<String, Object> beforeData,
            Map<String, Object> afterData,
            Map<String, Object> metadata, String actorDisplayName, String entityDisplayName,
            String entityDisplayReference, String eventSummary, String origin, String supportContext, String correlationId,
            Instant occurredAt
    ) {
        this.companyId = companyId;
        this.actorUserId = actorUserId;
        this.actorType = actorType;
        this.module = module;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.beforeData = beforeData;
        this.afterData = afterData;
        this.metadata = metadata;
        this.actorDisplayName=actorDisplayName; this.entityDisplayName=entityDisplayName;
        this.entityDisplayReference=entityDisplayReference; this.eventSummary=eventSummary;
        this.origin=origin; this.supportContext=supportContext; this.correlationId=correlationId;
        this.occurredAt = occurredAt;
    }
}
