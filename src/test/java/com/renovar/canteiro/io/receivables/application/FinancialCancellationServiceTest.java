package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.contracts.domain.Contract;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.application.ChangeRequestService;
import com.renovar.canteiro.io.payables.domain.PayableRepository;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationRepository;
import com.renovar.canteiro.io.receivables.domain.BillingInstallment;
import com.renovar.canteiro.io.receivables.domain.BillingInstallmentRepository;
import com.renovar.canteiro.io.receivables.domain.BillingInstallmentStatus;
import com.renovar.canteiro.io.receivables.domain.Invoice;
import com.renovar.canteiro.io.receivables.domain.InvoiceItem;
import com.renovar.canteiro.io.receivables.domain.InvoiceItemRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceItemStatus;
import com.renovar.canteiro.io.receivables.domain.InvoiceRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceStatus;
import com.renovar.canteiro.io.receivables.domain.Receivable;
import com.renovar.canteiro.io.receivables.domain.ReceivableBalance;
import com.renovar.canteiro.io.receivables.domain.ReceivableRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlement;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementEntryType;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementPaymentMethod;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementStatus;
import com.renovar.canteiro.io.receivables.domain.ReceivableStatus;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialCancellationServiceTest {
    @Mock private TenantContextHolder tenantContextHolder;
    @Mock private AccessAuthorizationService authorizationService;
    @Mock private ContractRepository contractRepository;
    @Mock private BillingInstallmentRepository installmentRepository;
    @Mock private BillingAllocationRepository allocationRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private InvoiceItemRepository invoiceItemRepository;
    @Mock private TaxObligationRepository taxObligationRepository;
    @Mock private PayableRepository payableRepository;
    @Mock private ReceivableRepository receivableRepository;
    @Mock private ReceivableSettlementRepository settlementRepository;
    @Mock private CalculateReceivableBalanceUseCase calculateReceivableBalanceUseCase;
    @Mock private ChangeRequestService changeRequestService;
    @Mock private AuditEventRecorder auditEventRecorder;

    @Test
    void reversesSettlementWithANegativeLinkedEntryAndReopensReceivable() {
        UUID companyId = UUID.randomUUID();
        UUID receivableId = UUID.randomUUID();
        UUID settlementId = UUID.randomUUID();
        Receivable receivable = receivable(receivableId, companyId, ReceivableStatus.RECEIVED);
        ReceivableSettlement original = ReceivableSettlement.rehydrate(settlementId, companyId, receivableId,
                receivable.getContractId(), LocalDate.of(2026, 8, 10), new BigDecimal("100.00"),
                ReceivableSettlementEntryType.SETTLEMENT, null, ReceivableSettlementPaymentMethod.PIX, null,
                ReceivableSettlementStatus.CONFIRMED, Instant.now(), Instant.now());
        directTenant(companyId);
        when(settlementRepository.findByIdAndCompanyId(settlementId, companyId)).thenReturn(Optional.of(original));
        when(receivableRepository.findByIdAndCompanyIdWithSettlementLock(receivableId, companyId)).thenReturn(Optional.of(receivable));
        when(settlementRepository.existsReversalOfSettlementIdAndCompanyId(settlementId, companyId)).thenReturn(false);
        when(settlementRepository.save(any())).thenAnswer(invocation -> {
            ReceivableSettlement value = invocation.getArgument(0);
            return ReceivableSettlement.rehydrate(UUID.randomUUID(), value.getCompanyId(), value.getReceivableId(),
                    value.getContractId(), value.getReceivedOn(), value.getAmount(), value.getEntryType(),
                    value.getReversalOfSettlementId(), value.getPaymentMethod(), value.getNote(), value.getStatus(),
                    Instant.now(), Instant.now());
        });
        when(calculateReceivableBalanceUseCase.calculate(companyId, receivableId)).thenReturn(
                new ReceivableBalance(receivableId, new BigDecimal("100.00"), BigDecimal.ZERO));
        when(receivableRepository.updateStatus(any())).thenAnswer(invocation -> invocation.getArgument(0));

        FinancialCancellationResult result = service().reverseSettlement(receivableId, settlementId,
                new CancelFinancialRecordCommand("Lançamento duplicado", null));

        assertEquals(ReceivableStatus.OVERDUE.name(), result.status());
        var captor = org.mockito.ArgumentCaptor.forClass(ReceivableSettlement.class);
        verify(settlementRepository).save(captor.capture());
        assertEquals(new BigDecimal("-100.00"), captor.getValue().getAmount());
        assertEquals(settlementId, captor.getValue().getReversalOfSettlementId());
    }

    @Test
    void refusesToCancelInstallmentWhileARegisteredInvoiceExists() {
        UUID companyId = UUID.randomUUID();
        UUID installmentId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        BillingInstallment installment = BillingInstallment.rehydrate(installmentId, companyId, contractId, "P-1", null,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10), BillingInstallmentStatus.OPEN,
                Instant.now(), Instant.now());
        directTenant(companyId);
        when(installmentRepository.findByIdAndCompanyId(installmentId, companyId)).thenReturn(Optional.of(installment));
        when(contractRepository.findByIdAndCompanyIdWithBillingLock(contractId, companyId))
                .thenReturn(Optional.of(mock(Contract.class)));
        when(invoiceRepository.existsRegisteredByBillingInstallmentIdAndCompanyId(installmentId, companyId)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service().cancelInstallment(installmentId,
                new CancelFinancialRecordCommand("Cancelamento comercial", null)));
    }

    @Test
    void cancelsInvoiceItemsAndWithholdingBeforeCancellingTheInvoice() {
        UUID companyId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        UUID receivableId = UUID.randomUUID();
        Invoice invoice = Invoice.rehydrate(invoiceId, companyId, contractId, "NF-1", null, null,
                LocalDate.of(2026, 8, 10), new BigDecimal("100.00"), InvoiceStatus.REGISTERED,
                Instant.now(), Instant.now());
        InvoiceItem item = InvoiceItem.create(companyId, invoiceId, contractId, UUID.randomUUID(), UUID.randomUUID(),
                "Service", new BigDecimal("100.00"));
        TaxObligation tax = TaxObligation.register(companyId, invoiceId, contractId, receivableId, "IRRF", null, null,
                new BigDecimal("10.00"), TaxTreatment.WITHHELD_BY_CUSTOMER);
        Receivable receivable = receivable(receivableId, companyId, ReceivableStatus.PARTIALLY_RECEIVED);
        directTenant(companyId);
        when(invoiceRepository.findByIdAndCompanyId(invoiceId, companyId)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.findByIdAndCompanyIdWithLock(invoiceId, companyId)).thenReturn(Optional.of(invoice));
        when(contractRepository.findByIdAndCompanyIdWithBillingLock(contractId, companyId))
                .thenReturn(Optional.of(mock(Contract.class)));
        when(taxObligationRepository.findByInvoiceIdAndCompanyId(invoiceId, companyId)).thenReturn(List.of(tax));
        when(payableRepository.findByTaxObligationIdAndCompanyId(tax.getId(), companyId)).thenReturn(Optional.empty());
        when(invoiceItemRepository.findByInvoiceIdAndCompanyId(invoiceId, companyId)).thenReturn(List.of(item));
        when(invoiceRepository.updateStatus(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(invoiceItemRepository.updateStatus(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(taxObligationRepository.updateStatus(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(receivableRepository.findByIdAndCompanyIdWithSettlementLock(receivableId, companyId))
                .thenReturn(Optional.of(receivable));
        when(calculateReceivableBalanceUseCase.calculate(companyId, receivableId)).thenReturn(
                new ReceivableBalance(receivableId, new BigDecimal("100.00"), new BigDecimal("20.00")));
        when(receivableRepository.updateStatus(any())).thenAnswer(invocation -> invocation.getArgument(0));

        FinancialCancellationResult result = service().cancelInvoice(invoiceId,
                new CancelFinancialRecordCommand("Nota emitida incorretamente", null));

        assertEquals(InvoiceStatus.CANCELLED.name(), result.status());
        verify(invoiceItemRepository).updateStatus(argThat(value -> value.getStatus() == InvoiceItemStatus.CANCELLED));
        verify(taxObligationRepository).updateStatus(argThat(value -> value.getStatus() == TaxObligationStatus.CANCELLED));
        verify(invoiceRepository).updateStatus(argThat(value -> value.getStatus() == InvoiceStatus.CANCELLED));
    }

    private void directTenant(UUID companyId) {
        when(tenantContextHolder.requireCurrentTenant()).thenReturn(new TenantContext(UUID.randomUUID(), companyId));
        when(authorizationService.requireChangeAuthorization(any(), any())).thenReturn(ChangeAuthorizationMode.DIRECT);
    }

    private Receivable receivable(UUID id, UUID companyId, ReceivableStatus status) {
        return Receivable.rehydrate(id, companyId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "R-1", null,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15), new BigDecimal("100.00"), status,
                Instant.now(), Instant.now());
    }

    private FinancialCancellationService service() {
        return new FinancialCancellationService(tenantContextHolder, authorizationService, contractRepository,
                installmentRepository, allocationRepository, invoiceRepository, invoiceItemRepository,
                taxObligationRepository, payableRepository, receivableRepository, settlementRepository,
                calculateReceivableBalanceUseCase, changeRequestService, auditEventRecorder,
                Clock.fixed(Instant.parse("2026-08-20T12:00:00Z"), ZoneOffset.UTC),org.mockito.Mockito.mock(ReceivableSettlementAttributionService.class));
    }
}
