package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;

import java.util.UUID;

public record ReceivableSettlementApprovalResponse(
        ReceivableSettlementResponse settlement,
        ReceivableBalanceResponse balance,
        UUID changeRequestId,
        ChangeAuthorizationMode mode
) {
}
