package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableRepository;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Projection helper only; the calling financial use case must have authorized its resource. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FinancialCurrencyService {
    private final TenantContextHolder tenants;
    private final ContractRepository contracts;
    private final ReceivableRepository receivables;

    public CurrencyCode forContract(UUID contractId) {
        return contracts.findByIdAndCompanyId(contractId, tenants.requireCurrentTenant().companyId())
                .orElseThrow(() -> new TenantResourceNotFoundException("Contract")).getCurrency();
    }

    public CurrencyCode forReceivable(UUID receivableId) {
        return forContract(receivables.findByIdAndCompanyId(receivableId, tenants.requireCurrentTenant().companyId())
                .orElseThrow(() -> new TenantResourceNotFoundException("Receivable")).getContractId());
    }
}
