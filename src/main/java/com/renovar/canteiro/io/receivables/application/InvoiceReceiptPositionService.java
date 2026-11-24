package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.receivables.domain.BillingAllocationRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceItemRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceItemStatus;
import com.renovar.canteiro.io.receivables.domain.InvoiceReceiptPosition;
import com.renovar.canteiro.io.receivables.domain.InvoiceSettlementRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceStatus;
import com.renovar.canteiro.io.receivables.domain.InvoiceSummary;
import com.renovar.canteiro.io.receivables.domain.ReceivableRepository;
import com.renovar.canteiro.io.receivables.domain.TaxObligationRepository;
import com.renovar.canteiro.io.receivables.domain.TaxObligationStatus;
import com.renovar.canteiro.io.receivables.domain.TaxTreatment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvoiceReceiptPositionService {
    private final InvoiceSettlementRepository ledger;
    private final InvoiceItemRepository items;
    private final BillingAllocationRepository allocations;
    private final ReceivableRepository titles;
    private final TaxObligationRepository taxes;

    public InvoiceReceiptPosition calculate(UUID company, InvoiceSummary invoice, boolean permitted, Instant now) {
        if (!permitted || invoice.status() != InvoiceStatus.REGISTERED) return InvoiceReceiptPosition.unavailable(invoice.status(), permitted, now);
        var lines = items.findByInvoiceIdAndCompanyId(invoice.id(), company);
        if (lines.isEmpty() || lines.stream().anyMatch(line -> line.getStatus() != InvoiceItemStatus.REGISTERED || !invoice.contractId().equals(line.getContractId()))
                || lines.stream().map(line -> line.getAllocatedAmount()).reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(invoice.totalAmount()) != 0)
            return unknown(now);
        for (var line : lines) {
            var allocation = allocations.findByIdAndCompanyId(line.getBillingAllocationId(), company);
            if (allocation.isEmpty() || !invoice.contractId().equals(allocation.get().getContractId())
                    || !java.util.Objects.equals(line.getContractServiceId(), allocation.get().getContractServiceId())) return unknown(now);
            var linked = titles.findAllByBillingInstallmentIdAndCompanyId(allocation.get().getBillingInstallmentId(), company);
            if (linked.size() != 1 || !invoice.contractId().equals(linked.getFirst().getContractId())) return unknown(now);
        }
        if (ledger.hasUnprovenInvoiceHistory(company, invoice.id())) return unknown(now);
        BigDecimal retained = taxes.findByInvoiceIdAndCompanyId(invoice.id(), company).stream()
                .filter(tax -> tax.getStatus() == TaxObligationStatus.REGISTERED && tax.getTreatment() == TaxTreatment.WITHHELD_BY_CUSTOMER)
                .map(tax -> tax.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal net = invoice.totalAmount().subtract(retained).setScale(2);
        BigDecimal received = ledger.receivedByInvoice(company, invoice.id()).setScale(2);
        if (net.signum() < 0 || received.signum() < 0 || received.compareTo(net) > 0) return unknown(now);
        BigDecimal outstanding = net.subtract(received);
        var status = outstanding.signum() == 0 ? received.signum() > 0 ? InvoiceReceiptPosition.Status.RECEIVED : InvoiceReceiptPosition.Status.SETTLED_BY_ADJUSTMENTS
                : received.signum() > 0 ? InvoiceReceiptPosition.Status.PARTIALLY_RECEIVED : InvoiceReceiptPosition.Status.UNPAID;
        return new InvoiceReceiptPosition(true, status, null, now, received, outstanding);
    }

    private InvoiceReceiptPosition unknown(Instant now) {
        return new InvoiceReceiptPosition(false, InvoiceReceiptPosition.Status.UNAVAILABLE, InvoiceReceiptPosition.Reason.HISTORY_REQUIRES_RECONCILIATION, now);
    }
}
