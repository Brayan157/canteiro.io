package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.contracts.domain.Contract;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.application.ChangeRequestService;
import com.renovar.canteiro.io.payables.domain.Payable;
import com.renovar.canteiro.io.payables.domain.PayableRepository;
import com.renovar.canteiro.io.receivables.domain.Invoice;
import com.renovar.canteiro.io.receivables.domain.InvoiceRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceStatus;
import com.renovar.canteiro.io.receivables.domain.ReceivableRepository;
import com.renovar.canteiro.io.receivables.domain.TaxObligation;
import com.renovar.canteiro.io.receivables.domain.TaxObligationRepository;
import com.renovar.canteiro.io.receivables.domain.TaxObligationStatus;
import com.renovar.canteiro.io.receivables.domain.TaxTreatment;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.tenancy.domain.TenantContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxObligationServiceTest {

    @Mock private TenantContextHolder tenantContextHolder;
    @Mock private AccessAuthorizationService accessAuthorizationService;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private ContractRepository contractRepository;
    @Mock private ReceivableRepository receivableRepository;
    @Mock private TaxObligationRepository taxObligationRepository;
    @Mock private PayableRepository payableRepository;
    @Mock private CalculateReceivableBalanceUseCase calculateReceivableBalanceUseCase;
    @Mock private ChangeRequestService changeRequestService;
    @Mock private AuditEventRecorder auditEventRecorder;

    @Test
    void createsWorkScopedPayableOnlyForCompanyPayableTax() {
        Fixture fixture = fixture();
        TaxObligationCreationResult result = fixture.service().create(new CreateTaxObligationCommand(
                fixture.invoiceId(), null, "ISS", "Municipal tax", LocalDate.of(2026, 8, 30), new BigDecimal("10.00"),
                TaxTreatment.PAYABLE_BY_COMPANY, null
        ));

        assertEquals(fixture.workId(), result.payable().getWorkId());
        assertEquals(fixture.contractId(), result.payable().getContractId());
        assertEquals(new BigDecimal("10.00"), result.payable().getOriginalAmount());
        assertEquals(TaxTreatment.PAYABLE_BY_COMPANY, result.taxObligation().getTreatment());
    }

    @Test
    void doesNotCreatePayableForInformationalTax() {
        Fixture fixture = fixture();
        TaxObligationCreationResult result = fixture.service().create(new CreateTaxObligationCommand(
                fixture.invoiceId(), null, "INSS", null, null, new BigDecimal("3.00"), TaxTreatment.INFORMATIONAL, null
        ));

        assertNull(result.payable());
        assertEquals(TaxTreatment.INFORMATIONAL, result.taxObligation().getTreatment());
    }

    @Test
    void rejectsTaxCreationWhenTheLockedInvoiceWasCancelledAfterInitialValidation() {
        Fixture fixture = fixture();
        Invoice cancelled = Invoice.rehydrate(fixture.invoiceId(), fixture.companyId(), fixture.contractId(), "NF-001",
                null, null, LocalDate.of(2026, 8, 20), new BigDecimal("100.00"), InvoiceStatus.CANCELLED,
                Instant.now(), Instant.now());
        when(invoiceRepository.findByIdAndCompanyIdWithLock(fixture.invoiceId(), cancelled.getCompanyId()))
                .thenReturn(Optional.of(cancelled));

        assertThrows(IllegalStateException.class, () -> fixture.service().create(new CreateTaxObligationCommand(
                fixture.invoiceId(), null, "ISS", null, null, new BigDecimal("10.00"),
                TaxTreatment.INFORMATIONAL, null
        )));
    }

    private Fixture fixture() {
        UUID companyId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        UUID workId = UUID.randomUUID();
        Invoice invoice = Invoice.rehydrate(invoiceId, companyId, contractId, "NF-001", null, null,
                LocalDate.of(2026, 8, 20), new BigDecimal("100.00"), InvoiceStatus.REGISTERED, Instant.now(), Instant.now());
        Contract contract = Contract.rehydrate(contractId, companyId, workId, null, "Tax contract", ContractStatus.ACTIVE,
                null, null, null, Instant.now(), Instant.now());
        when(tenantContextHolder.requireCurrentTenant()).thenReturn(new TenantContext(UUID.randomUUID(), companyId));
        when(accessAuthorizationService.requireChangeAuthorization(any(), any())).thenReturn(ChangeAuthorizationMode.DIRECT);
        when(invoiceRepository.findByIdAndCompanyId(invoiceId, companyId)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.findByIdAndCompanyIdWithLock(invoiceId, companyId)).thenReturn(Optional.of(invoice));
        when(contractRepository.findByIdAndCompanyId(contractId, companyId)).thenReturn(Optional.of(contract));
        lenient().when(taxObligationRepository.save(any())).thenAnswer(invocation -> {
            TaxObligation input = invocation.getArgument(0);
            return TaxObligation.rehydrate(UUID.randomUUID(), input.getCompanyId(), input.getInvoiceId(), input.getContractId(),
                    input.getReceivableId(), input.getName(), input.getDescription(), input.getDueOn(), input.getAmount(),
                    input.getTreatment(), TaxObligationStatus.REGISTERED, Instant.now(), Instant.now());
        });
        lenient().when(payableRepository.save(any())).thenAnswer(invocation -> {
            Payable input = invocation.getArgument(0);
            return Payable.rehydrate(UUID.randomUUID(), input.getCompanyId(), input.getFinancialScope(), input.getWorkId(),
                    input.getContractId(), input.getTaxObligationId(), input.getReference(), input.getDescription(),
                    input.getDueOn(), input.getOriginalAmount(), input.getStatus(), Instant.now(), Instant.now());
        });
        return new Fixture(companyId, invoiceId, contractId, workId, new TaxObligationService(
                tenantContextHolder, accessAuthorizationService, invoiceRepository, contractRepository, receivableRepository,
                taxObligationRepository, payableRepository, calculateReceivableBalanceUseCase, changeRequestService,
                auditEventRecorder
        ));
    }

    private record Fixture(UUID companyId, UUID invoiceId, UUID contractId, UUID workId, TaxObligationService service) {
    }
}
