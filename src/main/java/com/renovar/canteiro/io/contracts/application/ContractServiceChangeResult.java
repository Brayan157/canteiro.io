package com.renovar.canteiro.io.contracts.application;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.contracts.domain.ContractNetAmount;
import com.renovar.canteiro.io.contracts.domain.ContractService;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;

public record ContractServiceChangeResult(ContractService service, ContractNetAmount contractNetAmount,
                                          ChangeRequest changeRequest, ChangeAuthorizationMode mode) {
}
