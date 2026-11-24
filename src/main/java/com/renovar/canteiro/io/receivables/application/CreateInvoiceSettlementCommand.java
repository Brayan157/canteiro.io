package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementPaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateInvoiceSettlementCommand(UUID operationId, UUID receivableId, LocalDate receivedOn, BigDecimal amount,
        ReceivableSettlementPaymentMethod paymentMethod, String note, String justification, List<Line> items) {
    public record Line(UUID invoiceId, UUID invoiceItemId, UUID billingAllocationId, UUID measurementContractCompositionId, BigDecimal amount) { }
}
