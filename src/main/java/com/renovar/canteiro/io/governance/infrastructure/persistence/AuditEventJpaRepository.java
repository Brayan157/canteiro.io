package com.renovar.canteiro.io.governance.infrastructure.persistence;

import com.renovar.canteiro.io.governance.domain.AuditModule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface AuditEventJpaRepository extends JpaRepository<AuditEventJpaEntity, UUID> {

    Optional<AuditEventJpaEntity> findByIdAndCompanyId(UUID auditEventId, UUID companyId);

    Page<AuditEventJpaEntity> findByCompanyIdAndModuleNot(UUID companyId, AuditModule module, Pageable pageable);
}
