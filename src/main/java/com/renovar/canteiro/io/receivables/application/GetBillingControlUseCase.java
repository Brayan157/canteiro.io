package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.contracts.domain.Contract;
import com.renovar.canteiro.io.contracts.domain.ContractBillingControlFilter;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.contracts.domain.ContractServiceRepository;
import com.renovar.canteiro.io.contracts.domain.ContractService;
import com.renovar.canteiro.io.receivables.domain.BillingAllocation;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationStatus;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationRepository;
import com.renovar.canteiro.io.receivables.domain.BillingInstallmentRepository;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.List;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetBillingControlUseCase {
    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService authorizationService;
    private final ContractRepository contractRepository;
    private final ContractServiceRepository contractServiceRepository;
    private final BillingAllocationRepository billingAllocationRepository;
    private final BillingInstallmentRepository billingInstallmentRepository;
    private final CalculateContractBillingBalanceUseCase calculateContractBillingBalanceUseCase;
    private final CalculateInvoiceAllocationBalanceUseCase calculateInvoiceAllocationBalanceUseCase;
    private final NetBillingCapacityService netCapacities;
    private final com.renovar.canteiro.io.receivables.domain.ReceivableSettlementRepository settlements;
    private final com.renovar.canteiro.io.receivables.domain.ReceivableRepository receivables;

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Page<BillingControlSummary> find(ContractBillingControlFilter filter, Pageable pageable) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        authorizationService.requirePermission(AccessModule.BILLING, AccessAction.READ);
        var page = contractRepository.findBillingControl(companyId, filter, pageable);
        boolean canReadReceipts = authorizationService.hasPermission(AccessModule.RECEIVABLES, AccessAction.READ);
        Map<UUID, BigDecimal> received = canReadReceipts
                ? settlements.confirmedAmountsByContractIdsAndCompanyId(page.map(Contract::getId).getContent(), companyId)
                : Map.of();
        return page.map(contract -> new BillingControlSummary(contract,
                calculateContractBillingBalanceUseCase.calculate(companyId, contract.getId()),
                canReadReceipts ? received.getOrDefault(contract.getId(), BigDecimal.ZERO.setScale(2)) : null));
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public ContractBillingControl get(UUID contractId) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        authorizationService.requirePermission(AccessModule.BILLING, AccessAction.READ);
        Contract contract = contractRepository.findByIdAndCompanyId(contractId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Contract"));
        Map<UUID, ContractService> servicesById = contractServiceRepository.findByContractIdAndCompanyId(contractId, companyId).stream()
                .collect(Collectors.toMap(ContractService::getId, service -> service));
        var financialPositions = originalTitlePositions(companyId, contractId);
        var allocations = billingAllocationRepository.findByContractIdAndCompanyId(contractId, companyId).stream()
                .map(allocation -> new BillingAllocationControl(allocation,
                        billingInstallmentRepository.findByIdAndCompanyId(allocation.getBillingInstallmentId(), companyId)
                                .orElseThrow(() -> new TenantResourceNotFoundException("Billing installment")),
                        allocation.getContractServiceId() == null ? null : servicesById.get(allocation.getContractServiceId()).getName(),
                        calculateInvoiceAllocationBalanceUseCase.calculate(companyId, allocation.getId()),
                        financialPositions.get(allocation.getBillingInstallmentId())))
                .toList();
        var netCapacity = netCapacities.calculate(companyId, contractId);
        var services = servicesById.values().stream().map(service -> {
            BigDecimal activeAllocated = allocations.stream()
                    .filter(line -> service.getId().equals(line.allocation().getContractServiceId()))
                    .filter(line -> line.allocation().getStatus() == BillingAllocationStatus.ACTIVE)
                    .map(line -> line.allocation().getAllocatedAmount()).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2);
            BigDecimal issued = allocations.stream()
                    .filter(line -> service.getId().equals(line.allocation().getContractServiceId()))
                    .map(line -> line.invoiceBalance().registeredInvoiceAmount()).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2);
            BigDecimal activeCapacity = allocations.stream().filter(line -> service.getId().equals(line.allocation().getContractServiceId()))
                    .filter(line -> line.allocation().getStatus() == BillingAllocationStatus.ACTIVE)
                    .map(line -> line.allocation().getCapacityAmount()).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
            return new BillingServiceControl(service.getId(), service.getName(), service.getEffectiveNetAmount(), activeAllocated,
                    service.getEffectiveNetAmount().subtract(activeCapacity).setScale(2), issued, activeCapacity, netCapacity.availableForService(service.getId()));
        }).toList();
        BigDecimal received = authorizationService.hasPermission(AccessModule.RECEIVABLES, AccessAction.READ)
                ? settlements.confirmedAmountsByContractIdsAndCompanyId(List.of(contractId), companyId)
                        .getOrDefault(contractId, BigDecimal.ZERO.setScale(2)) : null;
        return new ContractBillingControl(contract,
                calculateContractBillingBalanceUseCase.calculate(companyId, contractId), services, allocations, received);
    }

    private Map<UUID, com.renovar.canteiro.io.receivables.domain.ReceivableFinancialPosition> originalTitlePositions(UUID company, UUID contract) {
        if (!authorizationService.hasPermission(AccessModule.RECEIVABLES, AccessAction.READ)) return Map.of();
        var filter = new com.renovar.canteiro.io.receivables.domain.ReceivableFilter(null,null,null,null,null,null,
                contract,null,null,null,null,null,null);
        Map<UUID, com.renovar.canteiro.io.receivables.domain.ReceivableFinancialPosition> statuses = new java.util.HashMap<>();
        int number = 0;
        org.springframework.data.domain.Page<com.renovar.canteiro.io.receivables.domain.ReceivableReadProjection> page;
        do {
            page = receivables.findByCompanyId(company, filter, org.springframework.data.domain.PageRequest.of(number++,100));
            for (var title : page) {
                if (title.renegotiationSourceReceivableId() == null) {
                    statuses.put(title.billingInstallmentId(), title.financialPosition());
                }
            }
        } while (page.hasNext());
        return statuses;
    }
}
