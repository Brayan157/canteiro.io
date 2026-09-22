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

    @Transactional(readOnly = true)
    public Page<BillingControlSummary> find(ContractBillingControlFilter filter, Pageable pageable) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        authorizationService.requirePermission(AccessModule.BILLING, AccessAction.READ);
        return contractRepository.findBillingControl(companyId, filter, pageable)
                .map(contract -> new BillingControlSummary(contract,
                        calculateContractBillingBalanceUseCase.calculate(companyId, contract.getId())));
    }

    @Transactional(readOnly = true)
    public ContractBillingControl get(UUID contractId) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        authorizationService.requirePermission(AccessModule.BILLING, AccessAction.READ);
        Contract contract = contractRepository.findByIdAndCompanyId(contractId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Contract"));
        Map<UUID, ContractService> servicesById = contractServiceRepository.findByContractIdAndCompanyId(contractId, companyId).stream()
                .collect(Collectors.toMap(ContractService::getId, service -> service));
        var allocations = billingAllocationRepository.findByContractIdAndCompanyId(contractId, companyId).stream()
                .map(allocation -> new BillingAllocationControl(allocation,
                        billingInstallmentRepository.findByIdAndCompanyId(allocation.getBillingInstallmentId(), companyId)
                                .orElseThrow(() -> new TenantResourceNotFoundException("Billing installment")),
                        allocation.getContractServiceId() == null ? null : servicesById.get(allocation.getContractServiceId()).getName(),
                        calculateInvoiceAllocationBalanceUseCase.calculate(companyId, allocation.getId())))
                .toList();
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
            return new BillingServiceControl(service.getId(), service.getName(), service.getNetAmount(), activeAllocated,
                    service.getNetAmount().subtract(activeAllocated).setScale(2), issued);
        }).toList();
        return new ContractBillingControl(contract,
                calculateContractBillingBalanceUseCase.calculate(companyId, contractId), services, allocations);
    }
}
