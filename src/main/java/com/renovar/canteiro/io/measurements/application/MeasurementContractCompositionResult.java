package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.contracts.domain.ContractService;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.measurements.domain.MeasurementContractComposition;

public record MeasurementContractCompositionResult(ContractService contractService,
                                                   MeasurementContractComposition composition,
                                                   ChangeRequest changeRequest,
                                                   ChangeAuthorizationMode authorizationMode,
                                                   boolean alreadyComposed) {
}
