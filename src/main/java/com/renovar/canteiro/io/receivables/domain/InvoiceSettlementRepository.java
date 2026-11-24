package com.renovar.canteiro.io.receivables.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceSettlementRepository {
    void lockOperation(UUID companyId, UUID operationId);
    Optional<Operation> findOperation(UUID companyId, UUID operationId);
    void remember(UUID companyId, UUID operationId, UUID receivableId, UUID contractId, String fingerprint, UUID settlementId, UUID requestId);
    void complete(UUID companyId, UUID operationId, UUID requestId, UUID settlementId);
    Page<Candidate> candidates(UUID companyId, UUID receivableId, Pageable pageable);
    Optional<Candidate> candidate(UUID companyId, UUID receivableId, UUID invoiceItemId);
    List<Line> lines(UUID companyId, UUID settlementId);
    void append(Line line);
    boolean hasUnprovenHistory(UUID companyId, UUID receivableId);
    boolean hasUnprovenInvoiceHistory(UUID companyId, UUID invoiceId);
    BigDecimal receivedByInvoice(UUID companyId, UUID invoiceId);
    List<WithholdingBasis> withholdingBasis(UUID companyId, UUID receivableId, List<UUID> invoiceIds);
    record WithholdingBasis(UUID invoiceId, UUID invoiceItemId, BigDecimal invoiceAmount, BigDecimal withheldAmount,
            boolean taxScopeProven, boolean itemOriginProven, BigDecimal itemAmount, BigDecimal itemReceivedAmount,
            long itemCount) { }
    record Operation(UUID operationId, UUID receivableId, String fingerprint, UUID settlementId, UUID requestId) { }
    record Candidate(UUID invoiceId, String invoiceNumber, UUID invoiceItemId, UUID billingAllocationId,
            UUID contractServiceId, String contractServiceName, UUID measurementContractCompositionId,
            BigDecimal invoicedItemAmount, BigDecimal itemReceivedAmount, BigDecimal invoiceNetAmount, BigDecimal invoiceReceivedAmount) { }
    record Line(UUID id, UUID companyId, UUID contractId, UUID receivableId, UUID settlementId, UUID invoiceId,
            UUID invoiceItemId, UUID billingAllocationId, UUID measurementContractCompositionId,
            BigDecimal amount, UUID reversalOfLineId, Instant createdAt) { }
}
