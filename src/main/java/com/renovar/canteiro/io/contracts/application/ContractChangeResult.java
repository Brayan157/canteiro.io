package com.renovar.canteiro.io.contracts.application;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.contracts.domain.Contract;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;

public record ContractChangeResult(Contract contract, ChangeRequest changeRequest, ChangeAuthorizationMode mode) {
}
