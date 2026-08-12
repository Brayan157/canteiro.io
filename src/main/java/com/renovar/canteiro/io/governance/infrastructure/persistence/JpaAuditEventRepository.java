package com.renovar.canteiro.io.governance.infrastructure.persistence;

import com.renovar.canteiro.io.governance.domain.AuditEvent;
import com.renovar.canteiro.io.governance.domain.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaAuditEventRepository implements AuditEventRepository {

    private final AuditEventJpaRepository auditEventJpaRepository;
    private final AuditEventPersistenceMapper auditEventPersistenceMapper;

    @Override
    public AuditEvent append(AuditEvent auditEvent) {
        if (auditEvent.getId() != null) {
            throw new IllegalStateException("Audit events are immutable and can only be appended");
        }
        return auditEventPersistenceMapper.toDomain(
                auditEventJpaRepository.save(auditEventPersistenceMapper.toJpaEntity(auditEvent))
        );
    }

    @Override
    public Optional<AuditEvent> findByIdAndCompanyId(UUID auditEventId, UUID companyId) {
        return auditEventJpaRepository.findByIdAndCompanyId(auditEventId, companyId)
                .map(auditEventPersistenceMapper::toDomain);
    }

    @Override
    public Page<AuditEvent> findByCompanyId(UUID companyId, Pageable pageable) {
        return auditEventJpaRepository.findByCompanyId(companyId, pageable).map(auditEventPersistenceMapper::toDomain);
    }
}
