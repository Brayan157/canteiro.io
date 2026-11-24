package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.contracts.domain.Contract;
import com.renovar.canteiro.io.contracts.domain.ContractBillingControlFilter;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import com.renovar.canteiro.io.contracts.domain.ContractServiceRepository;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationRepository;
import com.renovar.canteiro.io.receivables.domain.BillingInstallmentRepository;
import com.renovar.canteiro.io.receivables.domain.ContractBillingBalance;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.tenancy.domain.TenantContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.junit.jupiter.api.Assertions.assertNull;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.AccessAction;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class GetBillingControlUseCaseTest {
    @Mock private TenantContextHolder tenantContextHolder;
    @Mock private AccessAuthorizationService authorizationService;
    @Mock private ContractRepository contractRepository;
    @Mock private ContractServiceRepository contractServiceRepository;
    @Mock private BillingAllocationRepository allocationRepository;
    @Mock private BillingInstallmentRepository installmentRepository;
    @Mock private CalculateContractBillingBalanceUseCase balanceUseCase;
    @Mock private CalculateInvoiceAllocationBalanceUseCase invoiceBalanceUseCase;
    @Mock private NetBillingCapacityService netCapacities;
    @Mock private com.renovar.canteiro.io.receivables.domain.ReceivableSettlementRepository settlements;

    @Test
    void listsOnlyContractsReturnedForTheCurrentTenantWithDerivedBalances() {
        UUID companyId = UUID.randomUUID();
        Contract contract = Contract.rehydrate(UUID.randomUUID(), companyId, UUID.randomUUID(), "C-01", "Electrical",
                ContractStatus.ACTIVE, null, null, null, Instant.now(), Instant.now());
        when(tenantContextHolder.requireCurrentTenant()).thenReturn(new TenantContext(UUID.randomUUID(), companyId));
        when(contractRepository.findBillingControl(any(), any(), any())).thenReturn(new PageImpl<>(List.of(contract)));
        when(balanceUseCase.calculate(companyId, contract.getId())).thenReturn(new ContractBillingBalance(
                new BigDecimal("120.00"), new BigDecimal("20.00"), new BigDecimal("100.00"),
                new BigDecimal("40.00"), new BigDecimal("60.00")));

        var result = service().find(new ContractBillingControlFilter(null, ContractStatus.ACTIVE, "electric"),
                PageRequest.of(0, 20));

        assertEquals(1, result.getTotalElements());
        assertEquals(new BigDecimal("60.00"), result.getContent().getFirst().balance().availableAmount());
        assertNull(result.getContent().getFirst().receivedAmount());
        verifyNoInteractions(settlements);
        verify(contractRepository).findBillingControl(companyId,
                new ContractBillingControlFilter(null, ContractStatus.ACTIVE, "electric"), PageRequest.of(0, 20));
    }

    @Test
    void readsCashInOneTenantScopedBatchOnlyWithAdditionalReadPermission() {
        UUID company = UUID.randomUUID();
        Contract contract = Contract.rehydrate(UUID.randomUUID(), company, UUID.randomUUID(), "C-02", "Obra",
                ContractStatus.ACTIVE, null, null, null, Instant.now(), Instant.now());
        when(tenantContextHolder.requireCurrentTenant()).thenReturn(new TenantContext(UUID.randomUUID(), company));
        when(authorizationService.hasPermission(AccessModule.RECEIVABLES, AccessAction.READ)).thenReturn(true);
        when(contractRepository.findBillingControl(any(), any(), any())).thenReturn(new PageImpl<>(List.of(contract)));
        when(settlements.confirmedAmountsByContractIdsAndCompanyId(List.of(contract.getId()), company))
                .thenReturn(Map.of(contract.getId(), new BigDecimal("12.34")));
        var page = service().find(new ContractBillingControlFilter(null, null, null), PageRequest.of(0, 20));
        assertEquals(new BigDecimal("12.34"), page.getContent().getFirst().receivedAmount());
        verify(settlements).confirmedAmountsByContractIdsAndCompanyId(List.of(contract.getId()), company);
    }

    private GetBillingControlUseCase service() {
        return new GetBillingControlUseCase(tenantContextHolder, authorizationService, contractRepository,
                contractServiceRepository, allocationRepository, installmentRepository, balanceUseCase, invoiceBalanceUseCase, netCapacities, settlements,
                org.mockito.Mockito.mock(com.renovar.canteiro.io.receivables.domain.ReceivableRepository.class));
    }
}
