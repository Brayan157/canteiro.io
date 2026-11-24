package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.payables.domain.PayableRepository;
import com.renovar.canteiro.io.payables.domain.PayableStatus;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationRepository;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationStatus;
import com.renovar.canteiro.io.receivables.domain.BillingInstallmentRepository;
import com.renovar.canteiro.io.receivables.domain.BillingInstallmentStatus;
import com.renovar.canteiro.io.receivables.domain.Invoice;
import com.renovar.canteiro.io.receivables.domain.InvoiceItemRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceItemStatus;
import com.renovar.canteiro.io.receivables.domain.InvoiceStatus;
import com.renovar.canteiro.io.receivables.domain.Receivable;
import com.renovar.canteiro.io.receivables.domain.ReceivableBalance;
import com.renovar.canteiro.io.receivables.domain.ReceivableRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlement;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementEntryType;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementStatus;
import com.renovar.canteiro.io.receivables.domain.ReceivableStatus;
import com.renovar.canteiro.io.receivables.domain.TaxObligationRepository;
import com.renovar.canteiro.io.receivables.domain.TaxTreatment;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Conservative protection until an exact, manual invoice-item settlement ledger is available. */
@Service
@RequiredArgsConstructor
public class InvoiceCancellationSafetyGuard {
    private final InvoiceItemRepository items;
    private final BillingAllocationRepository allocations;
    private final BillingInstallmentRepository installments;
    private final ReceivableRepository receivables;
    private final ReceivableSettlementRepository settlements;
    private final CalculateReceivableBalanceUseCase balances;
    private final TaxObligationRepository taxes;
    private final PayableRepository payables;

    // The caller holds the contract/invoice locks throughout validation and application.
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireSafe(Invoice invoice) {
        if (invoice.getStatus() != InvoiceStatus.REGISTERED) throw review();
        UUID companyId = invoice.getCompanyId();
        var lines = items.findByInvoiceIdAndCompanyId(invoice.getId(), companyId);
        if (lines.isEmpty() || lines.stream().map(line -> line.getAllocatedAmount()).reduce(BigDecimal.ZERO, BigDecimal::add)
                .compareTo(invoice.getTotalAmount()) != 0) throw review();
        Map<UUID, Receivable> titles = new LinkedHashMap<>();
        for (var line : lines) {
            if (!companyId.equals(line.getCompanyId()) || !invoice.getId().equals(line.getInvoiceId())
                    || !invoice.getContractId().equals(line.getContractId()) || line.getStatus() != InvoiceItemStatus.REGISTERED) throw review();
            var allocation = allocations.findByIdAndCompanyId(line.getBillingAllocationId(), companyId).orElseThrow(this::review);
            if (!companyId.equals(allocation.getCompanyId()) || !invoice.getContractId().equals(allocation.getContractId())
                    || !Objects.equals(line.getContractServiceId(), allocation.getContractServiceId())
                    || allocation.getStatus() != BillingAllocationStatus.ACTIVE) throw review();
            var installment = installments.findByIdAndCompanyId(allocation.getBillingInstallmentId(), companyId).orElseThrow(this::review);
            if (!companyId.equals(installment.getCompanyId()) || !invoice.getContractId().equals(installment.getContractId())
                    || installment.getStatus() != BillingInstallmentStatus.OPEN) throw review();
            var linked = receivables.findAllByBillingInstallmentIdAndCompanyId(installment.getId(), companyId);
            // Multiple descendants require formal reconciliation, never attribution inferred by amount.
            if (linked.size() != 1) throw review();
            Receivable title = linked.getFirst();
            if (!companyId.equals(title.getCompanyId()) || !invoice.getContractId().equals(title.getContractId())
                    || !installment.getId().equals(title.getBillingInstallmentId())) throw review();
            titles.put(title.getId(), title);
        }
        for (UUID id : titles.keySet().stream().sorted(Comparator.comparing(UUID::toString)).toList()) {
            Receivable title = receivables.findByIdAndCompanyIdWithCancellationCheckLock(id, companyId).orElseThrow(this::review);
            if (!companyId.equals(title.getCompanyId()) || !invoice.getContractId().equals(title.getContractId())
                    || !titles.get(id).getBillingInstallmentId().equals(title.getBillingInstallmentId())
                    || title.getStatus() == ReceivableStatus.RENEGOTIATED || title.getStatus() == ReceivableStatus.CANCELLED) throw review();
            requireNoActiveReceipts(title);
        }
        for (var tax : taxes.findByInvoiceIdAndCompanyId(invoice.getId(), companyId).stream()
                .sorted(Comparator.comparing(value -> value.getId().toString())).toList()) {
            if (!companyId.equals(tax.getCompanyId()) || !invoice.getId().equals(tax.getInvoiceId())
                    || !invoice.getContractId().equals(tax.getContractId())
                    || tax.getReceivableId() != null && !titles.containsKey(tax.getReceivableId())) throw review();
            var payable = payables.findByTaxObligationIdAndCompanyIdWithLock(tax.getId(), companyId);
            if (tax.getTreatment() == TaxTreatment.PAYABLE_BY_COMPANY && payable.isEmpty()) throw review();
            payable.ifPresent(value -> {
                if (!companyId.equals(value.getCompanyId()) || !invoice.getContractId().equals(value.getContractId())
                        || !tax.getId().equals(value.getTaxObligationId()) || tax.getTreatment() != TaxTreatment.PAYABLE_BY_COMPANY) throw review();
                if (value.getStatus() == PayableStatus.PAID || value.getStatus() == PayableStatus.PARTIALLY_PAID) {
                    throw conflict("INVOICE_CANCELLATION_HAS_TAX_PAYMENTS", "A nota possui obrigação tributária com pagamento vigente.");
                }
                if (value.getStatus() != PayableStatus.OPEN && value.getStatus() != PayableStatus.OVERDUE) throw review();
            });
        }
    }

    private void requireNoActiveReceipts(Receivable title) {
        List<ReceivableSettlement> history = settlements.findByReceivableIdAndCompanyId(title.getId(), title.getCompanyId());
        if (history.stream().anyMatch(entry -> !title.getCompanyId().equals(entry.getCompanyId())
                || !title.getId().equals(entry.getReceivableId()) || !title.getContractId().equals(entry.getContractId())
                || entry.getStatus() != ReceivableSettlementStatus.CONFIRMED)) throw review();
        BigDecimal net = history.stream().map(ReceivableSettlement::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (net.signum() > 0) throw conflict("INVOICE_CANCELLATION_HAS_ACTIVE_RECEIPTS", "Há recebimento vigente nos títulos vinculados à nota.");
        if (net.signum() < 0) throw review();
        for (var original : history.stream().filter(entry -> entry.getEntryType() == ReceivableSettlementEntryType.SETTLEMENT).toList()) {
            var reversals = history.stream().filter(entry -> original.getId().equals(entry.getReversalOfSettlementId())).toList();
            if (reversals.size() != 1 || reversals.getFirst().getAmount().compareTo(original.getAmount().negate()) != 0) throw review();
        }
        for (var reversal : history.stream().filter(entry -> entry.getEntryType() == ReceivableSettlementEntryType.REVERSAL).toList()) {
            if (history.stream().noneMatch(entry -> entry.getEntryType() == ReceivableSettlementEntryType.SETTLEMENT
                    && entry.getId().equals(reversal.getReversalOfSettlementId()))) throw review();
        }
        ReceivableBalance balance;
        try {
            balance = balances.calculate(title.getCompanyId(), title.getId());
        } catch (IllegalArgumentException invalidHistory) {
            throw review();
        }
        if (balance.confirmedSettlementAmount().compareTo(net) != 0 || balance.creditedAmount().signum() != 0) throw review();
        if (history.isEmpty() && (title.getStatus() == ReceivableStatus.RECEIVED || title.getStatus() == ReceivableStatus.PARTIALLY_RECEIVED)
                && balance.netReceivableAmount().signum() > 0) throw review();
    }

    private ApiException review() {
        return conflict("INVOICE_CANCELLATION_REQUIRES_REVIEW", "Os vínculos ou o histórico financeiro exigem conciliação antes do cancelamento.");
    }

    private ApiException conflict(String reason, String message) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION, message, List.of(), reason);
    }
}
