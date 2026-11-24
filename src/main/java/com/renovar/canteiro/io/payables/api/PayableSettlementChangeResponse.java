package com.renovar.canteiro.io.payables.api;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
public record PayableSettlementChangeResponse(PayableSettlementResponse settlement, PayableBalanceResponse balance,
                                              ChangeAuthorizationMode authorizationMode) { }
