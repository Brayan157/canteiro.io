package com.renovar.canteiro.io.governance.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.governance.domain.AuditEvent;
import com.renovar.canteiro.io.governance.domain.AuditEventRepository;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Provides the tenant-scoped, read-only audit history. */
@Service
@RequiredArgsConstructor
public class AuditEventQueryService {

    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService accessAuthorizationService;
    private final AuditEventRepository auditEventRepository;

    @Transactional(readOnly = true)
    public Page<AuditEvent> findAll(Pageable pageable) {
        accessAuthorizationService.requirePermission(AccessModule.AUDIT, AccessAction.READ);
        return auditEventRepository.findByCompanyId(
                tenantContextHolder.requireCurrentTenant().companyId(),
                pageable
        );
    }
}
