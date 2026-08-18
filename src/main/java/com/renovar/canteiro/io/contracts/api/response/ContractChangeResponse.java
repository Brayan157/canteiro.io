package com.renovar.canteiro.io.contracts.api.response;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;

import java.util.UUID;

public record ContractChangeResponse(ContractResponse contract, UUID changeRequestId, ChangeAuthorizationMode mode) {
}
