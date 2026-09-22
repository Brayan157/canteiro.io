package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.contracts.domain.Contract;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.application.ChangeRequestService;
import com.renovar.canteiro.io.receivables.domain.BillingAllocation;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationRepository;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationStatus;
import com.renovar.canteiro.io.receivables.domain.ContractBillingBalance;
import com.renovar.canteiro.io.receivables.domain.Invoice;
import com.renovar.canteiro.io.receivables.domain.InvoiceItem;
import com.renovar.canteiro.io.receivables.domain.InvoiceItemRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceStatus;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.tenancy.domain.TenantContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceRegistrationServiceTest {

    @Mock private TenantContextHolder tenantContextHolder;
    @Mock private AccessAuthorizationService accessAuthorizationService;
    @Mock private ContractRepository contractRepository;
    @Mock private BillingAllocationRepository billingAllocationRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private InvoiceItemRepository invoiceItemRepository;
    @Mock private CalculateContractBillingBalanceUseCase calculateContractBillingBalanceUseCase;
    @Mock private ChangeRequestService changeRequestService;
    @Mock private AuditEventRecorder auditEventRecorder;

    @Test
    void registersAnInvoiceFromAnActiveAllocationOfTheSameContract() {
        Fixture fixture = fixture(new BigDecimal("0.00"));
        InvoiceRegistrationResult result = fixture.service().register(command(fixture.contractId(), fixture.allocationId(), "80.00"));

        assertEquals(ChangeAuthorizationMode.DIRECT, result.mode());
        assertEquals(fixture.invoiceId(), result.invoice().getId());
        assertEquals(new BigDecimal("80.00"), result.invoice().getTotalAmount());
        assertEquals(fixture.contractServiceId(), result.items().getFirst().getContractServiceId());
        assertEquals(InvoiceStatus.REGISTERED, result.invoice().getStatus());
    }

    @Test
    void rejectsAnInvoiceItemThatWouldExceedItsBillingAllocation() {
        Fixture fixture = fixture(new BigDecimal("90.00"));

        assertThrows(IllegalStateException.class,
                () -> fixture.service().register(command(fixture.contractId(), fixture.allocationId(), "20.00")));
    }

    @Test
    void allowsOneServiceAllocationToBeIssuedAcrossSeveralInvoicesWithoutExceedingIt() {
        Fixture fixture = fixture(new BigDecimal("0.00"), new BigDecimal("40.00"), new BigDecimal("100.00"));

        InvoiceRegistrationResult first = fixture.service().register(
                command(fixture.contractId(), fixture.allocationId(), "NF-001", "40.00")
        );
        InvoiceRegistrationResult second = fixture.service().register(
                command(fixture.contractId(), fixture.allocationId(), "NF-002", "60.00")
        );

        assertEquals(new BigDecimal("40.00"), first.invoice().getTotalAmount());
        assertEquals(new BigDecimal("60.00"), second.invoice().getTotalAmount());
        assertThrows(IllegalStateException.class, () -> fixture.service().register(
                command(fixture.contractId(), fixture.allocationId(), "NF-003", "0.01")
        ));
    }

    @Test
    void rejectsPositiveInvoiceItemsThatOutrunTheNetActiveAllocations() {
        Fixture fixture = fixture(new BigDecimal("0.00"));
        when(calculateContractBillingBalanceUseCase.calculate(fixture.companyId(), fixture.contractId()))
                .thenReturn(new ContractBillingBalance(
                        new BigDecimal("90.00"), new BigDecimal("90.00"), BigDecimal.ZERO.setScale(2)));

        assertThrows(BillingLimitExceededException.class, () -> fixture.service().register(
                command(fixture.contractId(), fixture.allocationId(), "100.00")
        ));
    }

    private Fixture fixture(BigDecimal alreadyRegistered) {
        return fixture(alreadyRegistered, alreadyRegistered, alreadyRegistered);
    }

    private Fixture fixture(BigDecimal... registeredAmounts) {
        UUID companyId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        UUID allocationId = UUID.randomUUID();
        UUID contractServiceId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        Contract contract = Contract.rehydrate(contractId, companyId, UUID.randomUUID(), "C-01", "Invoice contract",
                ContractStatus.ACTIVE, null, null, null, Instant.now(), Instant.now());
        BillingAllocation allocation = BillingAllocation.rehydrate(
                allocationId, companyId, UUID.randomUUID(), contractId, contractServiceId,
                com.renovar.canteiro.io.receivables.domain.BillingAllocationType.SERVICE, new BigDecimal("100.00"),
                BillingAllocationStatus.ACTIVE, Instant.now(), Instant.now()
        );
        when(tenantContextHolder.requireCurrentTenant()).thenReturn(new TenantContext(UUID.randomUUID(), companyId));
        when(accessAuthorizationService.requireChangeAuthorization(any(), any())).thenReturn(ChangeAuthorizationMode.DIRECT);
        when(contractRepository.findByIdAndCompanyId(contractId, companyId)).thenReturn(Optional.of(contract));
        when(contractRepository.findByIdAndCompanyIdWithBillingLock(contractId, companyId)).thenReturn(Optional.of(contract));
        when(billingAllocationRepository.findByContractIdAndCompanyId(contractId, companyId)).thenReturn(List.of(allocation));
        when(billingAllocationRepository.findByIdAndCompanyId(allocationId, companyId)).thenReturn(Optional.of(allocation));
        when(invoiceItemRepository.registeredAmountByBillingAllocationIdAndCompanyId(allocationId, companyId))
                .thenReturn(registeredAmounts[0], java.util.Arrays.copyOfRange(registeredAmounts, 1, registeredAmounts.length));
        lenient().when(invoiceItemRepository.registeredAmountByContractIdAndCompanyId(contractId, companyId))
                .thenReturn(registeredAmounts[0], java.util.Arrays.copyOfRange(registeredAmounts, 1, registeredAmounts.length));
        lenient().when(calculateContractBillingBalanceUseCase.calculate(companyId, contractId))
                .thenReturn(new ContractBillingBalance(
                        new BigDecimal("100.00"), new BigDecimal("100.00"), BigDecimal.ZERO.setScale(2)));
        lenient().when(invoiceRepository.save(any())).thenAnswer(invocation -> {
            Invoice input = invocation.getArgument(0);
            return Invoice.rehydrate(invoiceId, input.getCompanyId(), input.getContractId(), input.getNumber(),
                    input.getSeries(), input.getAccessKey(), input.getIssuedOn(), input.getTotalAmount(),
                    InvoiceStatus.REGISTERED, Instant.now(), Instant.now());
        });
        lenient().when(invoiceItemRepository.save(any())).thenAnswer(invocation -> {
            InvoiceItem input = invocation.getArgument(0);
            return InvoiceItem.rehydrate(UUID.randomUUID(), input.getCompanyId(), input.getInvoiceId(), input.getContractId(),
                    input.getBillingAllocationId(), input.getContractServiceId(), input.getDescription(),
                    input.getAllocatedAmount(), input.getStatus(), Instant.now(), Instant.now());
        });
        return new Fixture(companyId, contractId, allocationId, contractServiceId, invoiceId,
                new InvoiceRegistrationService(tenantContextHolder, accessAuthorizationService, contractRepository,
                        billingAllocationRepository, invoiceRepository, invoiceItemRepository,
                        new CalculateInvoiceAllocationBalanceUseCase(billingAllocationRepository, invoiceItemRepository),
                        calculateContractBillingBalanceUseCase,
                        changeRequestService, auditEventRecorder));
    }

    private CreateRegisteredInvoiceCommand command(UUID contractId, UUID allocationId, String amount) {
        return command(contractId, allocationId, "NF-001", amount);
    }

    private CreateRegisteredInvoiceCommand command(UUID contractId, UUID allocationId, String number, String amount) {
        return new CreateRegisteredInvoiceCommand(
                contractId, number, "1", null, LocalDate.of(2026, 8, 20),
                List.of(new InvoiceItemCommand(allocationId, "Service", new BigDecimal(amount))), null
        );
    }

    private record Fixture(UUID companyId, UUID contractId, UUID allocationId, UUID contractServiceId, UUID invoiceId,
                           InvoiceRegistrationService service) {
    }
}
