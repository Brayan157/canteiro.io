package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.contracts.domain.ContractServiceRepository;
import com.renovar.canteiro.io.receivables.domain.BillingAllocation;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationRepository;
import com.renovar.canteiro.io.receivables.domain.BillingInstallment;
import com.renovar.canteiro.io.receivables.domain.BillingInstallmentFilter;
import com.renovar.canteiro.io.receivables.domain.BillingInstallmentRepository;
import com.renovar.canteiro.io.receivables.domain.BillingInstallmentSummary;
import com.renovar.canteiro.io.receivables.domain.ReceivableRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableFinancialPosition;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ReadBillingInstallmentsUseCase {
    private final TenantContextHolder tenants;
    private final AccessAuthorizationService authorization;
    private final BillingInstallmentRepository installments;
    private final BillingAllocationRepository allocations;
    private final ContractRepository contracts;
    private final ContractServiceRepository services;
    private final ReceivableRepository receivables;

    public Page<Summary> find(BillingInstallmentFilter filter, Pageable pageable) {
        UUID company = requireBillingRead();
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_PAGINATION, "Page offset is too large.");
        }
        var page = installments.findByCompanyId(company, filter, pageable);
        var positions = originalPositions(page.stream().map(BillingInstallmentSummary::id).toList(), company);
        return page.map(value -> new Summary(value, positions.get(value.id())));
    }

    public Details get(UUID installmentId) {
        UUID company = requireBillingRead();
        BillingInstallment installment = installments.findByIdAndCompanyId(installmentId, company)
                .orElseThrow(() -> new TenantResourceNotFoundException("Billing installment"));
        var contract = contracts.findByIdAndCompanyId(installment.getContractId(), company)
                .orElseThrow(() -> new TenantResourceNotFoundException("Contract"));
        Map<UUID, String> names = new HashMap<>();
        var lines = allocations.findByBillingInstallmentIdAndCompanyId(installmentId, company).stream()
                .sorted(Comparator.comparing(BillingAllocation::getId)).map(allocation -> {
                    if (!installment.getContractId().equals(allocation.getContractId())) throw new TenantResourceNotFoundException("Billing allocation");
                    String name = allocation.getContractServiceId() == null ? null : names.computeIfAbsent(allocation.getContractServiceId(), id ->
                            services.findByIdAndCompanyId(id, company).filter(service -> service.getContractId().equals(contract.getId()))
                                    .orElseThrow(() -> new TenantResourceNotFoundException("Contract service")).getName());
                    return new Allocation(allocation, name);
                }).toList();
        BigDecimal netAmount = lines.stream().map(line -> line.allocation().getAllocatedAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
        boolean referencesAvailable = authorization.hasPermission(AccessModule.RECEIVABLES, AccessAction.READ);
        List<ReceivableReference> references = referencesAvailable
                ? receivables.findAllByBillingInstallmentIdAndCompanyId(installmentId, company).stream().map(receivable -> {
                    if (!contract.getId().equals(receivable.getContractId())) throw new TenantResourceNotFoundException("Receivable");
                    return new ReceivableReference(receivable.getId(), receivable.getReference());
                }).sorted(Comparator.comparing(ReceivableReference::id)).toList() : List.of();
        var position = originalPositions(List.of(installmentId), company).get(installmentId);
        return new Details(installment, contract.getCurrency(), netAmount, lines, referencesAvailable, references, position);
    }

    private Map<UUID, ReceivableFinancialPosition> originalPositions(List<UUID> ids, UUID company) {
        if (ids.isEmpty() || !authorization.hasPermission(AccessModule.RECEIVABLES, AccessAction.READ)) return Map.of();
        return receivables.findOriginalPositionsByBillingInstallmentIdsAndCompanyId(ids, company);
    }

    private UUID requireBillingRead() {
        UUID company = tenants.requireCurrentTenant().companyId();
        authorization.requirePermission(AccessModule.BILLING, AccessAction.READ);
        return company;
    }

    public record Details(BillingInstallment installment, CurrencyCode currency, BigDecimal netAmount,
                          List<Allocation> allocations, boolean receivableReferencesAvailable, List<ReceivableReference> receivables,
                          ReceivableFinancialPosition receivableFinancialPosition) {
    }
    public record Summary(BillingInstallmentSummary installment, ReceivableFinancialPosition receivableFinancialPosition) {
    }
    public record Allocation(BillingAllocation allocation, String contractServiceName) {
    }
    public record ReceivableReference(UUID id, String reference) {
    }
}
