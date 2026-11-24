package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.receivables.domain.InvoiceContractSummary;
import com.renovar.canteiro.io.receivables.domain.InvoiceRepository;
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
public class ReadInvoiceContractsUseCase {
    private final TenantContextHolder tenants;
    private final AccessAuthorizationService authorization;
    private final InvoiceRepository invoices;

    public Page<InvoiceContractSummary> find(String search, Pageable pageable) {
        UUID companyId = requireRead();
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_PAGINATION, "Page offset is too large.");
        }
        String normalized = search == null || search.isBlank() ? null : search.trim();
        if (normalized != null && normalized.length() > 200) {
            throw new IllegalArgumentException("Invoice contract search exceeds 200 characters");
        }
        return invoices.findContractsWithInvoices(companyId, normalized, pageable);
    }

    public InvoiceContractSummary get(UUID contractId) {
        return invoices.findContractWithInvoicesByIdAndCompanyId(contractId, requireRead())
                .orElseThrow(() -> new TenantResourceNotFoundException("Invoice contract"));
    }

    private UUID requireRead() {
        UUID companyId = tenants.requireCurrentTenant().companyId();
        authorization.requirePermission(AccessModule.INVOICES, AccessAction.READ);
        return companyId;
    }
}
