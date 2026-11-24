package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.receivables.domain.ReceivableRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementFilter;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementHistory;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementRepository;
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
public class ReadReceivableSettlementsUseCase {
    private final TenantContextHolder tenants;
    private final AccessAuthorizationService authorization;
    private final ReceivableRepository receivables;
    private final ReceivableSettlementRepository settlements;

    public Page<ReceivableSettlementHistory> find(UUID receivableId, ReceivableSettlementFilter filter, Pageable pageable) {
        UUID company = tenants.requireCurrentTenant().companyId();
        authorization.requirePermission(AccessModule.RECEIVABLES, AccessAction.READ);
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_PAGINATION, "Page offset is too large.");
        }
        var title = receivables.findReadByIdAndCompanyId(receivableId, company)
                .orElseThrow(() -> new TenantResourceNotFoundException("Receivable"));
        return settlements.findHistory(company, title.id(), title.contractId(), filter, pageable);
    }
}
