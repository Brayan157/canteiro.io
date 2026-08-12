package com.renovar.canteiro.io.governance.infrastructure.persistence;

import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import com.renovar.canteiro.io.governance.domain.ChangeRequestStatus;
import com.renovar.canteiro.io.shared.infrastructure.persistence.jpa.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Entity
@Table(name = "change_request")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChangeRequestJpaEntity extends BaseJpaEntity {

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "requester_user_id", nullable = false)
    private UUID requesterUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "module", nullable = false)
    private AuditModule module;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false)
    private ChangeRequestOperation operation;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "entity_version", nullable = false)
    private long entityVersion;

    @Column(name = "revision", nullable = false)
    private int revision;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ChangeRequestStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_data", columnDefinition = "jsonb")
    private Map<String, Object> beforeData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "proposed_data", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> proposedData;

    @Column(name = "justification")
    private String justification;

    @Column(name = "decided_by_user_id")
    private UUID decidedByUserId;

    @Column(name = "decision_reason")
    private String decisionReason;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public ChangeRequestJpaEntity(
            UUID companyId,
            UUID requesterUserId,
            AuditModule module,
            ChangeRequestOperation operation,
            String entityType,
            UUID entityId,
            long entityVersion,
            int revision,
            ChangeRequestStatus status,
            Map<String, Object> beforeData,
            Map<String, Object> proposedData,
            String justification
    ) {
        this.companyId = companyId;
        this.requesterUserId = requesterUserId;
        this.module = module;
        this.operation = operation;
        this.entityType = entityType;
        this.entityId = entityId;
        this.entityVersion = entityVersion;
        this.revision = revision;
        this.status = status;
        this.beforeData = beforeData;
        this.proposedData = proposedData;
        this.justification = justification;
    }

    void applyDecision(ChangeRequest changeRequest) {
        this.status = changeRequest.getStatus();
        this.decidedByUserId = changeRequest.getDecidedByUserId();
        this.decisionReason = changeRequest.getDecisionReason();
        this.decidedAt = changeRequest.getDecidedAt();
    }
}
