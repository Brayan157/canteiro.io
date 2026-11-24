package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.contracts.application.CalculateContractNetAmountUseCase;
import com.renovar.canteiro.io.contracts.domain.Contract;
import com.renovar.canteiro.io.receivables.domain.Invoice;
import com.renovar.canteiro.io.receivables.domain.InvoiceFinancialSnapshotRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CaptureInvoiceFinancialSnapshotService {
    private final CalculateContractNetAmountUseCase amounts;
    private final ReceivableSettlementRepository settlements;
    private final InvoiceFinancialSnapshotRepository snapshots;
    private final Clock clock;

    /** Caller owns the contract billing lock; snapshot and effective registration commit together. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void capture(Contract lockedContract, Invoice invoice) {
        var company = invoice.getCompanyId();
        var contract = invoice.getContractId();
        var received = settlements.confirmedAmountsByContractIdsAndCompanyId(List.of(contract), company)
                .getOrDefault(contract, BigDecimal.ZERO.setScale(2));
        snapshots.insert(company, contract, invoice.getId(), lockedContract.getCurrency(), clock.instant(),
                amounts.calculate(company, contract).netAmount(), received);
    }
}
