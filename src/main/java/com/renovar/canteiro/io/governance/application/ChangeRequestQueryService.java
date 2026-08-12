package com.renovar.canteiro.io.governance.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestRepository;
import com.renovar.canteiro.io.governance.domain.ChangeRequestStatus;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provides the governance-only view of pending proposals.
 *
 * <p>Change requests are not operational records. They remain in this separate view until their
 * proposal is applied by the owning use case after an approval.</p>
 */
@Service
@RequiredArgsConstructor
public class ChangeRequestQueryService {

    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService accessAuthorizationService;
    private final ChangeRequestRepository changeRequestRepository;

    @Transactional(readOnly = true)
    public Page<ChangeRequest> findPending(Pageable pageable) {
        accessAuthorizationService.requirePermission(AccessModule.AUDIT, AccessAction.READ);
        return changeRequestRepository.findByCompanyIdAndStatus(
                tenantContextHolder.requireCurrentTenant().companyId(),
                ChangeRequestStatus.PENDING,
                pageable
        );
    }
}
