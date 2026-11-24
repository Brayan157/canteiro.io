package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.receivables.domain.ReceivableFilter;
import com.renovar.canteiro.io.receivables.domain.ReceivableReadProjection;
import com.renovar.canteiro.io.receivables.domain.ReceivableRepository;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ReadReceivablesUseCase {
    private final TenantContextHolder tenants;
    private final AccessAuthorizationService authorization;
    private final ReceivableRepository receivables;

    public Page<ReceivableReadProjection> find(ReceivableFilter filter, Pageable pageable) {
        UUID company = requireRead();
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_PAGINATION, "Page offset is too large.");
        }
        return receivables.findByCompanyId(company, filter, pageable);
    }

    public ReceivableReadProjection get(UUID receivableId) {
        return receivables.findReadByIdAndCompanyId(receivableId, requireRead())
                .orElseThrow(() -> new TenantResourceNotFoundException("Receivable"));
    }

    private UUID requireRead() {
        UUID company = tenants.requireCurrentTenant().companyId();
        authorization.requirePermission(AccessModule.RECEIVABLES, AccessAction.READ);
        return company;
    }
}
