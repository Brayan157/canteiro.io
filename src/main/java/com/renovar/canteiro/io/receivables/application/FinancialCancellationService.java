package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.access.domain.ChangeOperation;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.application.ChangeRequestService;
import com.renovar.canteiro.io.governance.application.CreateChangeRequestCommand;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.AuditPayload;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import com.renovar.canteiro.io.governance.domain.ChangeRequestSnapshot;
import com.renovar.canteiro.io.payables.domain.PayableRepository;
import com.renovar.canteiro.io.receivables.domain.BillingAllocation;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationRepository;
import com.renovar.canteiro.io.receivables.domain.BillingInstallment;
import com.renovar.canteiro.io.receivables.domain.BillingInstallmentRepository;
import com.renovar.canteiro.io.receivables.domain.Invoice;
import com.renovar.canteiro.io.receivables.domain.InvoiceItem;
import com.renovar.canteiro.io.receivables.domain.InvoiceItemRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceRepository;
import com.renovar.canteiro.io.receivables.domain.Receivable;
import com.renovar.canteiro.io.receivables.domain.ReceivableBalance;
import com.renovar.canteiro.io.receivables.domain.ReceivableRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlement;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableStatus;
import com.renovar.canteiro.io.receivables.domain.TaxObligation;
import com.renovar.canteiro.io.receivables.domain.TaxObligationRepository;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FinancialCancellationService {
    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService authorizationService;
    private final ContractRepository contractRepository;
    private final BillingInstallmentRepository installmentRepository;
    private final BillingAllocationRepository allocationRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final TaxObligationRepository taxObligationRepository;
    private final PayableRepository payableRepository;
    private final ReceivableRepository receivableRepository;
    private final ReceivableSettlementRepository settlementRepository;
    private final CalculateReceivableBalanceUseCase calculateReceivableBalanceUseCase;
    private final ChangeRequestService changeRequestService;
    private final AuditEventRecorder auditEventRecorder;
    private final Clock clock;
    private final ReceivableSettlementAttributionService attribution;

    @Transactional
    public FinancialCancellationResult cancelInstallment(UUID installmentId, CancelFinancialRecordCommand command) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        validateReason(command);
        BillingInstallment installment = installmentRepository.findByIdAndCompanyId(installmentId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Billing installment"));
        return authorizeOrApply(AccessModule.BILLING, AuditModule.BILLING, "BillingInstallment", installmentId,
                installment.getStatus().name(), command, () -> persistInstallmentCancellation(companyId, installmentId, command.reason(), "direct", null));
    }

    @Transactional
    public FinancialCancellationResult cancelInvoice(UUID invoiceId, CancelFinancialRecordCommand command) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        validateReason(command);
        Invoice invoice = invoiceRepository.findByIdAndCompanyId(invoiceId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Invoice"));
        return authorizeOrApply(AccessModule.INVOICES, AuditModule.INVOICES, "Invoice", invoiceId,
                invoice.getStatus().name(), command, () -> persistInvoiceCancellation(companyId, invoiceId, command.reason(), "direct", null));
    }

    @Transactional
    public FinancialCancellationResult reverseSettlement(UUID receivableId, UUID settlementId,
                                                         CancelFinancialRecordCommand command) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        validateReason(command);
        ReceivableSettlement settlement = settlementRepository.findByIdAndCompanyId(settlementId, companyId)
                .filter(value -> value.getReceivableId().equals(receivableId))
                .orElseThrow(() -> new TenantResourceNotFoundException("Receivable settlement"));
        return authorizeOrApply(AccessModule.RECEIVABLES, AuditModule.RECEIVABLES, "ReceivableSettlement", settlementId,
                settlement.getStatus().name(), command,
                () -> persistSettlementReversal(companyId, receivableId, settlementId, command.reason(), "direct", null));
    }

    @Transactional
    public void applyApprovedCancellation(ChangeRequest request) {
        String reason = (String) request.getSnapshot().proposedData().values().get("reason");
        switch (request.getEntityType()) {
            case "BillingInstallment" -> persistInstallmentCancellation(request.getCompanyId(), request.getEntityId(), reason,
                    "approved-change-request", request.getId());
            case "Invoice" -> persistInvoiceCancellation(request.getCompanyId(), request.getEntityId(), reason,
                    "approved-change-request", request.getId());
            case "ReceivableSettlement" -> persistSettlementReversal(request.getCompanyId(),
                    UUID.fromString((String) request.getSnapshot().proposedData().values().get("receivableId")),
                    request.getEntityId(), reason, "approved-change-request", request.getId());
            default -> throw new IllegalStateException("Unsupported financial cancellation entity");
        }
    }

    private FinancialCancellationResult authorizeOrApply(AccessModule accessModule, AuditModule auditModule,
                                                          String entityType, UUID entityId, String previousStatus,
                                                          CancelFinancialRecordCommand command,
                                                          java.util.function.Supplier<FinancialCancellationResult> action) {
        ChangeAuthorizationMode mode = authorizationService.requireChangeAuthorization(accessModule, ChangeOperation.CANCEL);
        if (mode == ChangeAuthorizationMode.DIRECT) return action.get();
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("reason", command.reason().trim());
        if (entityType.equals("ReceivableSettlement")) {
            ReceivableSettlement settlement = settlementRepository.findByIdAndCompanyId(entityId,
                    tenantContextHolder.requireCurrentTenant().companyId()).orElseThrow();
            after.put("receivableId", settlement.getReceivableId().toString());
        }
        ChangeRequest request = changeRequestService.create(new CreateChangeRequestCommand(auditModule,
                ChangeRequestOperation.CANCEL, entityType, entityId, 0,
                new ChangeRequestSnapshot(new AuditPayload(Map.of("status", previousStatus)), new AuditPayload(after)),
                command.justification()));
        return new FinancialCancellationResult(entityId, null, previousStatus, request, mode);
    }

    private FinancialCancellationResult persistInstallmentCancellation(UUID companyId, UUID installmentId, String reason,
                                                                       String origin, UUID changeRequestId) {
        BillingInstallment installment = installmentRepository.findByIdAndCompanyId(installmentId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Billing installment"));
        contractRepository.findByIdAndCompanyIdWithBillingLock(installment.getContractId(), companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Contract"));
        if (invoiceRepository.existsRegisteredByBillingInstallmentIdAndCompanyId(installmentId, companyId)) {
            throw new IllegalStateException("Billing installment invoices must be cancelled first");
        }
        Receivable receivable = receivableRepository.findByBillingInstallmentIdAndCompanyId(installmentId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Receivable"));
        receivable = receivableRepository.findByIdAndCompanyIdWithSettlementLock(receivable.getId(), companyId).orElseThrow();
        if(receivable.getStatus()==ReceivableStatus.RENEGOTIATED)throw new IllegalStateException("Reverse the active renegotiation before cancelling billing");
        if (settlementRepository.confirmedAmountByReceivableIdAndCompanyId(receivable.getId(), companyId).signum() != 0) {
            throw new IllegalStateException("Billing installment settlements must be reversed first");
        }
        for (BillingAllocation allocation : allocationRepository.findByBillingInstallmentIdAndCompanyId(installmentId, companyId)) {
            BillingAllocation cancelled = allocationRepository.updateStatus(allocation.cancel());
            audit(AuditModule.BILLING, "BillingAllocation", cancelled.getId(), allocation.getStatus().name(),
                    cancelled.getStatus().name(), reason, origin, changeRequestId);
        }
        Receivable cancelledReceivable = receivableRepository.updateStatus(receivable.cancel());
        BillingInstallment cancelledInstallment = installmentRepository.updateStatus(installment.cancel());
        audit(AuditModule.RECEIVABLES, "Receivable", cancelledReceivable.getId(), receivable.getStatus().name(),
                cancelledReceivable.getStatus().name(), reason, origin, changeRequestId);
        audit(AuditModule.BILLING, "BillingInstallment", cancelledInstallment.getId(), installment.getStatus().name(),
                cancelledInstallment.getStatus().name(), reason, origin, changeRequestId);
        return new FinancialCancellationResult(installmentId, null, cancelledInstallment.getStatus().name(), null,
                ChangeAuthorizationMode.DIRECT);
    }

    private FinancialCancellationResult persistInvoiceCancellation(UUID companyId, UUID invoiceId, String reason,
                                                                   String origin, UUID changeRequestId) {
        Invoice invoice = invoiceRepository.findByIdAndCompanyIdWithLock(invoiceId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Invoice"));
        contractRepository.findByIdAndCompanyIdWithBillingLock(invoice.getContractId(), companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Contract"));
        for (TaxObligation tax : taxObligationRepository.findByInvoiceIdAndCompanyId(invoiceId, companyId)) {
            if(tax.getReceivableId()!=null&&receivableRepository.findByIdAndCompanyId(tax.getReceivableId(),companyId)
                .filter(r->r.getStatus()==ReceivableStatus.RENEGOTIATED).isPresent())
                throw new IllegalStateException("Reverse the title renegotiation before changing its withholding");
            payableRepository.findByTaxObligationIdAndCompanyId(tax.getId(), companyId)
                    .ifPresent(payable -> {
                        payableRepository.updateStatus(payable.cancel());
                        audit(AuditModule.PAYABLES, "Payable", payable.getId(), payable.getStatus().name(), "CANCELLED",
                                reason, origin, changeRequestId);
                    });
            taxObligationRepository.updateStatus(tax.cancel());
            audit(AuditModule.INVOICES, "TaxObligation", tax.getId(), tax.getStatus().name(), "CANCELLED", reason,
                    origin, changeRequestId);
        }
        for (InvoiceItem item : invoiceItemRepository.findByInvoiceIdAndCompanyId(invoiceId, companyId)) {
            invoiceItemRepository.updateStatus(item.cancel());
            audit(AuditModule.INVOICES, "InvoiceItem", item.getId(), item.getStatus().name(), "CANCELLED", reason,
                    origin, changeRequestId);
        }
        Invoice cancelled = invoiceRepository.updateStatus(invoice.cancel());
        audit(AuditModule.INVOICES, "Invoice", invoiceId, invoice.getStatus().name(), cancelled.getStatus().name(), reason,
                origin, changeRequestId);
        refreshAffectedReceivables(invoiceId, companyId);
        return new FinancialCancellationResult(invoiceId, null, cancelled.getStatus().name(), null, ChangeAuthorizationMode.DIRECT);
    }

    private void refreshAffectedReceivables(UUID invoiceId, UUID companyId) {
        taxObligationRepository.findByInvoiceIdAndCompanyId(invoiceId, companyId).stream()
                .map(TaxObligation::getReceivableId).filter(java.util.Objects::nonNull).distinct().forEach(receivableId -> {
                    Receivable receivable = receivableRepository.findByIdAndCompanyIdWithSettlementLock(receivableId, companyId)
                            .orElseThrow(() -> new TenantResourceNotFoundException("Receivable"));
                    ReceivableBalance balance = calculateReceivableBalanceUseCase.calculate(companyId, receivableId);
                    receivableRepository.updateStatus(receivable.withStatus(balance.statusOn(receivable, LocalDate.now(clock))));
                });
    }

    private FinancialCancellationResult persistSettlementReversal(UUID companyId, UUID receivableId, UUID settlementId,
                                                                  String reason, String origin, UUID changeRequestId) {
        Receivable receivable = receivableRepository.findByIdAndCompanyIdWithSettlementLock(receivableId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Receivable"));
        if(receivable.getStatus()==ReceivableStatus.RENEGOTIATED||receivable.getStatus()==ReceivableStatus.CANCELLED)
            throw new IllegalStateException("Reopen the original title before reversing its receipt");
        ReceivableSettlement original = settlementRepository.findByIdAndCompanyId(settlementId, companyId)
                .filter(value -> value.getReceivableId().equals(receivableId))
                .orElseThrow(() -> new TenantResourceNotFoundException("Receivable settlement"));
        if (settlementRepository.existsReversalOfSettlementIdAndCompanyId(settlementId, companyId)) {
            throw new IllegalStateException("Receivable settlement is already reversed");
        }
        ReceivableSettlement reversal = settlementRepository.save(ReceivableSettlement.reverse(
                original, LocalDate.now(clock), reason));
        attribution.attribute(reversal);
        ReceivableBalance balance = calculateReceivableBalanceUseCase.calculate(companyId, receivableId);
        Receivable updated = receivableRepository.updateStatus(receivable.withStatus(balance.statusOn(receivable, LocalDate.now(clock))));
        auditEventRecorder.recordDirectAction(AuditModule.RECEIVABLES, AuditAction.CANCEL, "ReceivableSettlement",
                original.getId(), Map.of("status", original.getStatus().name(), "amount", original.getAmount()),
                Map.of("reversalId", reversal.getId().toString(), "reversalAmount", reversal.getAmount()),
                metadata(reason, origin, changeRequestId));
        audit(AuditModule.RECEIVABLES, "Receivable", receivableId, receivable.getStatus().name(), updated.getStatus().name(),
                reason, origin, changeRequestId);
        return new FinancialCancellationResult(settlementId, reversal.getId(), updated.getStatus().name(), null,
                ChangeAuthorizationMode.DIRECT);
    }

    private void audit(AuditModule module, String type, UUID id, String before, String after, String reason,
                       String origin, UUID changeRequestId) {
        auditEventRecorder.recordDirectAction(module, AuditAction.CANCEL, type, id, Map.of("status", before),
                Map.of("status", after), metadata(reason, origin, changeRequestId));
    }

    private Map<String, Object> metadata(String reason, String origin, UUID changeRequestId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("reason", reason);
        metadata.put("origin", origin);
        if (changeRequestId != null) metadata.put("changeRequestId", changeRequestId.toString());
        return metadata;
    }

    private void validateReason(CancelFinancialRecordCommand command) {
        if (command == null || command.reason() == null || command.reason().isBlank()) {
            throw new IllegalArgumentException("Financial cancellation reason is required");
        }
    }
}
