package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.receivables.domain.Receivable;
import com.renovar.canteiro.io.receivables.domain.ReceivableBalance;
import com.renovar.canteiro.io.receivables.domain.ReceivableRepository;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetReceivableBalanceUseCase {

    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService accessAuthorizationService;
    private final ReceivableRepository receivableRepository;
    private final CalculateReceivableBalanceUseCase calculateReceivableBalanceUseCase;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ReceivableBalanceSnapshot get(UUID receivableId) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        accessAuthorizationService.requirePermission(AccessModule.RECEIVABLES, AccessAction.READ);
        Receivable receivable = receivableRepository.findByIdAndCompanyId(receivableId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Receivable"));
        ReceivableBalance balance = calculateReceivableBalanceUseCase.calculate(companyId, receivableId);
        return new ReceivableBalanceSnapshot(receivable, balance, balance.statusOn(receivable, LocalDate.now(clock)));
    }
}
