package com.renovar.canteiro.io.governance.infrastructure.persistence;

import com.renovar.canteiro.io.governance.domain.AuditEvent;
import com.renovar.canteiro.io.governance.domain.AuditPayload;
import org.springframework.stereotype.Component;

@Component
public class AuditEventPersistenceMapper {

    public AuditEventJpaEntity toJpaEntity(AuditEvent auditEvent) {
        return new AuditEventJpaEntity(
                auditEvent.getCompanyId(),
                auditEvent.getActorUserId(),
                auditEvent.getActorType(),
                auditEvent.getModule(),
                auditEvent.getAction(),
                auditEvent.getEntityType(),
                auditEvent.getEntityId(),
                auditEvent.getBeforeData() == null ? null : auditEvent.getBeforeData().values(),
                auditEvent.getAfterData() == null ? null : auditEvent.getAfterData().values(),
                auditEvent.getMetadata().values(),
                auditEvent.getOccurredAt()
        );
    }

    public AuditEvent toDomain(AuditEventJpaEntity entity) {
        return AuditEvent.rehydrate(
                entity.getId(),
                entity.getCompanyId(),
                entity.getActorUserId(),
                entity.getActorType(),
                entity.getModule(),
                entity.getAction(),
                entity.getEntityType(),
                entity.getEntityId(),
                entity.getBeforeData() == null ? null : new AuditPayload(entity.getBeforeData()),
                entity.getAfterData() == null ? null : new AuditPayload(entity.getAfterData()),
                new AuditPayload(entity.getMetadata()),
                entity.getOccurredAt(),
                entity.getCreatedAt()
        );
    }
}
