package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementPaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateReceivableSettlementCommand(
        UUID receivableId,
        LocalDate receivedOn,
        BigDecimal amount,
        ReceivableSettlementPaymentMethod paymentMethod,
        String note,
        String justification
) {
}
