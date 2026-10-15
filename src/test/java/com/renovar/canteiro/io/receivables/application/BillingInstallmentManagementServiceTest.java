package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.contracts.domain.Contract;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.contracts.domain.ContractService;
import com.renovar.canteiro.io.contracts.domain.ContractServiceRepository;
import com.renovar.canteiro.io.contracts.domain.ContractServiceStatus;
import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.application.ChangeRequestService;
import com.renovar.canteiro.io.measurements.domain.MeasurementContractCompositionRepository;
import com.renovar.canteiro.io.receivables.domain.BillingAllocation;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationRepository;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationStatus;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationType;
import com.renovar.canteiro.io.receivables.domain.BillingInstallment;
import com.renovar.canteiro.io.receivables.domain.BillingInstallmentRepository;
import com.renovar.canteiro.io.receivables.domain.BillingInstallmentStatus;
import com.renovar.canteiro.io.receivables.domain.Receivable;
import com.renovar.canteiro.io.receivables.domain.ReceivableRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableStatus;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.tenancy.domain.TenantContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingInstallmentManagementServiceTest {

    @Mock private TenantContextHolder tenantContextHolder;
    @Mock private AccessAuthorizationService accessAuthorizationService;
    @Mock private ContractRepository contractRepository;
    @Mock private ContractServiceRepository contractServiceRepository;
    @Mock private MeasurementContractCompositionRepository measurementContractCompositionRepository;
    @Mock private com.renovar.canteiro.io.measurements.domain.MeasurementContinuityRepository measurementContinuityRepository;
    @Mock private BillingInstallmentRepository billingInstallmentRepository;
    @Mock private BillingAllocationRepository billingAllocationRepository;
    @Mock private ReceivableRepository receivableRepository;
    @Mock private ValidateContractBillingLimitUseCase validateContractBillingLimitUseCase;
    @Mock private ChangeRequestService changeRequestService;
    @Mock private AuditEventRecorder auditEventRecorder;

    @Test
    void createsAnApprovedInstallmentWithActiveAllocationsAndOneReceivable() {
        UUID companyId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID installmentId = UUID.randomUUID();
        UUID receivableId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        Contract contract = Contract.rehydrate(contractId, companyId, UUID.randomUUID(), "C-01", "Contract",
                ContractStatus.ACTIVE, null, null, null, Instant.now(), Instant.now());
        ContractService service = ContractService.create(companyId, contractId, "Assembly", null,
                ContractServiceStatus.ACTIVE, BigDecimal.ONE, new BigDecimal("120.00"));
        BillingInstallment savedInstallment = BillingInstallment.rehydrate(installmentId, companyId, contractId,
                "FAT-001", "Approved installment", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15),
                BillingInstallmentStatus.OPEN, Instant.now(), Instant.now());

        when(tenantContextHolder.requireCurrentTenant()).thenReturn(new TenantContext(UUID.randomUUID(), companyId));
        when(accessAuthorizationService.requireChangeAuthorization(any(), any())).thenReturn(ChangeAuthorizationMode.DIRECT);
        when(contractRepository.findByIdAndCompanyId(contractId, companyId)).thenReturn(Optional.of(contract));
        when(contractRepository.findByIdAndCompanyIdWithBillingLock(contractId, companyId)).thenAnswer(invocation -> {
            org.mockito.Mockito.verifyNoInteractions(contractServiceRepository);
            return Optional.of(contract);
        });
        when(contractServiceRepository.findByIdAndCompanyId(serviceId, companyId)).thenReturn(Optional.of(service));
        when(billingInstallmentRepository.save(any())).thenReturn(savedInstallment);
        when(billingAllocationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(receivableRepository.save(any())).thenAnswer(invocation -> {
            Receivable input = invocation.getArgument(0);
            return Receivable.rehydrate(receivableId, input.getCompanyId(), input.getContractId(),
                    input.getBillingInstallmentId(), input.getFinancialGroupId(), input.getReference(),
                    input.getDescription(), input.getIssuedOn(), input.getDueOn(), input.getOriginalAmount(),
                    ReceivableStatus.OPEN, Instant.now(), Instant.now());
        });
        BillingInstallmentManagementService serviceUnderTest = new BillingInstallmentManagementService(
                tenantContextHolder, accessAuthorizationService, contractRepository, contractServiceRepository,
                measurementContractCompositionRepository, measurementContinuityRepository, billingInstallmentRepository, billingAllocationRepository, receivableRepository,
                validateContractBillingLimitUseCase, changeRequestService, auditEventRecorder
        );

        BillingInstallmentApprovalResult result = serviceUnderTest.createApproved(
                new CreateApprovedBillingInstallmentCommand(
                        contractId, "FAT-001", "Approved installment", LocalDate.of(2026, 8, 1),
                        LocalDate.of(2026, 8, 15), groupId,
                        List.of(
                                new BillingAllocationCommand(serviceId, BillingAllocationType.SERVICE, new BigDecimal("120.00")),
                                new BillingAllocationCommand(null, BillingAllocationType.CONTRACT_DISCOUNT, new BigDecimal("-20.00"))
                        ), null
                )
        );

        assertEquals(ChangeAuthorizationMode.DIRECT, result.mode());
        assertEquals(installmentId, result.billingInstallment().getId());
        assertEquals(2, result.allocations().size());
        assertEquals(BillingAllocationStatus.ACTIVE, result.allocations().getFirst().getStatus());
        assertEquals(new BigDecimal("100.00"), result.receivable().getOriginalAmount());
        assertEquals(groupId, result.receivable().getFinancialGroupId());
        ArgumentCaptor<List<BillingAllocation>> allocationCaptor = ArgumentCaptor.forClass(List.class);
        verify(validateContractBillingLimitUseCase).requireAvailableForActivation(
                org.mockito.ArgumentMatchers.eq(companyId), org.mockito.ArgumentMatchers.eq(contractId), allocationCaptor.capture()
        );
        assertEquals(new BigDecimal("100.00"), allocationCaptor.getValue().stream()
                .map(BillingAllocation::getAllocatedAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}
