package com.renovar.canteiro.io.governance.infrastructure.persistence;

import com.renovar.canteiro.io.governance.domain.AuditPayload;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestSnapshot;
import org.springframework.stereotype.Component;

@Component
public class ChangeRequestPersistenceMapper {

    public ChangeRequestJpaEntity toJpaEntity(ChangeRequest changeRequest) {
        return new ChangeRequestJpaEntity(
                changeRequest.getCompanyId(),
                changeRequest.getRequesterUserId(),
                changeRequest.getModule(),
                changeRequest.getOperation(),
                changeRequest.getEntityType(),
                changeRequest.getEntityId(),
                changeRequest.getEntityVersion(),
                changeRequest.getRevision(),
                changeRequest.getStatus(),
                changeRequest.getSnapshot().beforeData() == null ? null : changeRequest.getSnapshot().beforeData().values(),
                changeRequest.getSnapshot().proposedData().values(),
                changeRequest.getJustification()
        );
    }

    public ChangeRequest toDomain(ChangeRequestJpaEntity entity) {
        return ChangeRequest.rehydrate(
                entity.getId(),
                entity.getCompanyId(),
                entity.getRequesterUserId(),
                entity.getModule(),
                entity.getOperation(),
                entity.getEntityType(),
                entity.getEntityId(),
                entity.getEntityVersion(),
                entity.getRevision(),
                entity.getStatus(),
                new ChangeRequestSnapshot(
                        entity.getBeforeData() == null ? null : new AuditPayload(entity.getBeforeData()),
                        new AuditPayload(entity.getProposedData())
                ),
                entity.getJustification(),
                entity.getDecidedByUserId(),
                entity.getDecisionReason(),
                entity.getDecidedAt(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public void updateJpaEntity(ChangeRequestJpaEntity entity, ChangeRequest changeRequest) {
        entity.applyDecision(changeRequest);
    }
}
