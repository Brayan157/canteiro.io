package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementPaymentMethod;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementStatus;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementEntryType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ReceivableSettlementResponse(
        UUID id,
        UUID receivableId,
        UUID contractId,
        LocalDate receivedOn,
        BigDecimal amount,
        ReceivableSettlementEntryType entryType,
        UUID reversalOfSettlementId,
        ReceivableSettlementPaymentMethod paymentMethod,
        String note,
        ReceivableSettlementStatus status
) {
}
