package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.receivables.domain.ReceivableStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record ReceivableBalanceResponse(
        UUID receivableId,
        BigDecimal originalAmount,
        BigDecimal withheldAmount,
        BigDecimal netReceivableAmount,
        BigDecimal confirmedSettlementAmount,
        BigDecimal outstandingAmount,
        ReceivableStatus status
) {
}
