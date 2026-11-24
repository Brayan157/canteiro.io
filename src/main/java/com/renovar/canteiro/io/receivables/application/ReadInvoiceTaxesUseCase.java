package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableRepository;
import com.renovar.canteiro.io.receivables.domain.TaxObligation;
import com.renovar.canteiro.io.receivables.domain.TaxObligationRepository;
import com.renovar.canteiro.io.receivables.domain.TaxObligationStatus;
import com.renovar.canteiro.io.receivables.domain.TaxTreatment;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ReadInvoiceTaxesUseCase {
    private final TenantContextHolder tenants;
    private final AccessAuthorizationService authorization;
    private final InvoiceRepository invoices;
    private final ContractRepository contracts;
    private final TaxObligationRepository taxes;
    private final ReceivableRepository receivables;

    public Page<Entry> find(UUID invoiceId, TaxObligationStatus status, TaxTreatment treatment, Pageable pageable) {
        UUID company = tenants.requireCurrentTenant().companyId();
        authorization.requirePermission(AccessModule.INVOICES, AccessAction.READ);
        var invoice = invoices.findByIdAndCompanyId(invoiceId, company)
                .orElseThrow(() -> new TenantResourceNotFoundException("Invoice"));
        var contract = contracts.findByIdAndCompanyId(invoice.getContractId(), company)
                .orElseThrow(() -> new TenantResourceNotFoundException("Contract"));
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_PAGINATION, "Page offset is too large.");
        }
        Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Order.desc("createdAt"));
        Pageable ordered = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort.and(Sort.by("id")));
        Set<UUID> checkedReceivables = new HashSet<>();
        return taxes.findPageByInvoiceIdAndCompanyIdAndContractId(invoiceId, company, contract.getId(), status, treatment, ordered)
                .map(tax -> {
                    if (!company.equals(tax.getCompanyId()) || !invoiceId.equals(tax.getInvoiceId()) || !contract.getId().equals(tax.getContractId())) {
                        throw new TenantResourceNotFoundException("Tax obligation");
                    }
                    if (tax.getReceivableId() != null && checkedReceivables.add(tax.getReceivableId())) {
                        receivables.findByIdAndCompanyId(tax.getReceivableId(), company)
                                .filter(title -> contract.getId().equals(title.getContractId()))
                                .orElseThrow(() -> new TenantResourceNotFoundException("Tax obligation"));
                    }
                    return new Entry(tax, contract.getCurrency());
                });
    }

    public record Entry(TaxObligation tax, CurrencyCode currency) { }
}
