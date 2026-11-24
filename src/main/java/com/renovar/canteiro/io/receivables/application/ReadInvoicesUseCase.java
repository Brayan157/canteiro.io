package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.contracts.domain.ContractServiceRepository;
import com.renovar.canteiro.io.receivables.domain.BillingAllocation;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceFilter;
import com.renovar.canteiro.io.receivables.domain.InvoiceItem;
import com.renovar.canteiro.io.receivables.domain.InvoiceItemRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceSummary;
import com.renovar.canteiro.io.receivables.domain.InvoiceReceiptPosition;
import java.time.Clock;
import java.time.Instant;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ReadInvoicesUseCase {
    private final TenantContextHolder tenants;
    private final AccessAuthorizationService authorization;
    private final InvoiceRepository invoices;
    private final InvoiceItemRepository items;
    private final BillingAllocationRepository allocations;
    private final ContractRepository contracts;
    private final ContractServiceRepository services;
    private final Clock clock;
    private final InvoiceReceiptPositionService receiptPositions;

    public Page<Summary> find(InvoiceFilter filter, Pageable pageable) {
        UUID company = requireRead();
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_PAGINATION, "Page offset is too large.");
        }
        boolean canReadReceipts = authorization.hasPermission(AccessModule.RECEIVABLES, AccessAction.READ);
        Instant now = clock.instant();
        return invoices.findByCompanyId(company, filter, pageable).map(invoice ->
                new Summary(invoice, receiptPositions.calculate(company, invoice, canReadReceipts, now)));
    }

    public Details get(UUID invoiceId) {
        UUID company = requireRead();
        var invoice = invoices.findByIdAndCompanyId(invoiceId, company)
                .orElseThrow(() -> new TenantResourceNotFoundException("Invoice"));
        var contract = contracts.findByIdAndCompanyId(invoice.getContractId(), company)
                .orElseThrow(() -> new TenantResourceNotFoundException("Contract"));
        Map<UUID, BillingAllocation> allocationCache = new HashMap<>();
        Map<UUID, String> serviceNames = new HashMap<>();
        List<Line> lines = items.findByInvoiceIdAndCompanyId(invoiceId, company).stream()
                .sorted(Comparator.comparing(InvoiceItem::getId)).map(item -> {
                    if (!contract.getId().equals(item.getContractId())) throw new TenantResourceNotFoundException("Invoice item");
                    var allocation = allocationCache.computeIfAbsent(item.getBillingAllocationId(), id ->
                            allocations.findByIdAndCompanyId(id, company)
                                    .orElseThrow(() -> new TenantResourceNotFoundException("Billing allocation")));
                    if (!contract.getId().equals(allocation.getContractId())
                            || !Objects.equals(item.getContractServiceId(), allocation.getContractServiceId())) {
                        throw new TenantResourceNotFoundException("Billing allocation");
                    }
                    String name = item.getContractServiceId() == null ? null : serviceNames.computeIfAbsent(item.getContractServiceId(), id ->
                            services.findByIdAndCompanyId(id, company).filter(service -> contract.getId().equals(service.getContractId()))
                                    .orElseThrow(() -> new TenantResourceNotFoundException("Contract service")).getName());
                    return new Line(item, allocation.getBillingInstallmentId(), name, allocation.getMeasurementContractCompositionId());
                }).toList();
        var summary = new InvoiceSummary(invoice.getId(), contract.getId(), contract.getName(), contract.getReference(),
                contract.getContractIdentifier(), invoice.getNumber(), invoice.getSeries(), invoice.getIssuedOn(), invoice.getStatus(),
                contract.getCurrency(), invoice.getTotalAmount(), invoice.getCreatedAt(), invoice.getUpdatedAt());
        return new Details(summary, lines, receiptPositions.calculate(company, summary,
                authorization.hasPermission(AccessModule.RECEIVABLES, AccessAction.READ), clock.instant()));
    }

    private UUID requireRead() {
        UUID company = tenants.requireCurrentTenant().companyId();
        authorization.requirePermission(AccessModule.INVOICES, AccessAction.READ);
        return company;
    }

    public record Summary(InvoiceSummary invoice, InvoiceReceiptPosition receiptPosition) { }
    public record Details(InvoiceSummary invoice, List<Line> items, InvoiceReceiptPosition receiptPosition) { }
    public record Line(InvoiceItem item, UUID billingInstallmentId, String contractServiceName, UUID measurementContractCompositionId) { }
}
