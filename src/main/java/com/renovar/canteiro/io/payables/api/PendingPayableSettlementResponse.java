package com.renovar.canteiro.io.payables.api;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;

import java.util.UUID;

public record PendingPayableSettlementResponse(UUID changeRequestId, ChangeAuthorizationMode authorizationMode) { }
