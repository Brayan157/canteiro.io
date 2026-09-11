package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.receivables.domain.ReceivableBalance;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlement;
import com.renovar.canteiro.io.receivables.domain.ReceivableStatus;

public record ReceivableSettlementResult(
        ReceivableSettlement settlement,
        ReceivableBalance balance,
        ReceivableStatus receivableStatus,
        ChangeRequest changeRequest,
        ChangeAuthorizationMode mode
) {
}
