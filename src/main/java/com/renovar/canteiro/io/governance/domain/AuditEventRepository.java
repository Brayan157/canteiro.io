package com.renovar.canteiro.io.governance.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface AuditEventRepository {

    AuditEvent append(AuditEvent auditEvent);

    Optional<AuditEvent> findByIdAndCompanyId(UUID auditEventId, UUID companyId);

    Page<AuditEvent> findByCompanyId(UUID companyId, Pageable pageable);
}
