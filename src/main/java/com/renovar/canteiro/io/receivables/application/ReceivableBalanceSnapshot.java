package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.receivables.domain.Receivable;
import com.renovar.canteiro.io.receivables.domain.ReceivableBalance;
import com.renovar.canteiro.io.receivables.domain.ReceivableStatus;

public record ReceivableBalanceSnapshot(
        Receivable receivable,
        ReceivableBalance balance,
        ReceivableStatus currentStatus
) {
}
