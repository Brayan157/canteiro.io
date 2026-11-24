package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.contracts.domain.ContractServiceRepository;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationRepository;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationStatus;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationType;
import com.renovar.canteiro.io.receivables.domain.BillingInstallmentRepository;
import com.renovar.canteiro.io.receivables.domain.BillingInstallmentStatus;
import com.renovar.canteiro.io.receivables.domain.InvoiceItemRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceServiceCoverage;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class ReadInvoiceCoverageUseCase {
    private final TenantContextHolder tenants;
    private final AccessAuthorizationService authorization;
    private final BillingInstallmentRepository installments;
    private final BillingAllocationRepository allocations;
    private final InvoiceItemRepository items;
    private final ContractRepository contracts;
    private final ContractServiceRepository services;
    private final java.time.Clock clock;
    @Transactional(readOnly=true)
    public View read(UUID id){
        authorization.requirePermission(AccessModule.INVOICES,AccessAction.READ);
        authorization.requirePermission(AccessModule.BILLING,AccessAction.READ);
        UUID company=tenants.requireCurrentTenant().companyId();
        var title=installments.findByIdAndCompanyId(id,company).orElseThrow(()->new TenantResourceNotFoundException("Billing installment"));
        var contract=contracts.findByIdAndCompanyId(title.getContractId(),company).orElseThrow(()->new TenantResourceNotFoundException("Contract"));
        var lines=allocations.findByBillingInstallmentIdAndCompanyId(id,company).stream().filter(a->a.getStatus()==BillingAllocationStatus.ACTIVE).toList();
        var amounts=items.registeredAmountsByBillingAllocationIdsAndCompanyId(lines.stream().map(a->a.getId()).toList(),company);
        var groups=InvoiceServiceCoverage.groups(company,contract.getId(),lines,amounts);
        Map<UUID,String> names=services.findByContractIdAndCompanyId(contract.getId(),company).stream()
                .collect(java.util.stream.Collectors.toMap(s->s.getId(),s->s.getName()));
        boolean available=title.getStatus()==BillingInstallmentStatus.OPEN&&!lines.isEmpty()
                &&lines.stream().allMatch(a->a.getType()==BillingAllocationType.SERVICE&&a.getAllocatedAmount().signum()>0)
                &&groups.stream().noneMatch(g->g.state()==InvoiceServiceCoverage.State.REVIEW_REQUIRED);
        return new View(title.getId(),contract.getId(),contract.getCurrency().name(),available,groups,names,clock.instant());
    }
    public record View(UUID billingInstallmentId,UUID contractId,String currency,boolean available,List<InvoiceServiceCoverage.Group> groups,
            Map<UUID,String> serviceNames,java.time.Instant calculatedAt) {
        public java.math.BigDecimal amount(){return available?groups.stream().map(InvoiceServiceCoverage.Group::amount).reduce(java.math.BigDecimal.ZERO.setScale(2),java.math.BigDecimal::add):null;}
        public java.math.BigDecimal documentedAmount(){return available?groups.stream().map(InvoiceServiceCoverage.Group::documentedAmount).reduce(java.math.BigDecimal.ZERO.setScale(2),java.math.BigDecimal::add):null;}
        public java.math.BigDecimal remainingToDocumentAmount(){return available?amount().subtract(documentedAmount()):null;}
    }
}
