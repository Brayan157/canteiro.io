package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.contracts.domain.ContractService;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;

public record MeasurementItemConversionResult(ContractService contractService, ChangeRequest changeRequest,
                                              ChangeAuthorizationMode authorizationMode, boolean alreadyConverted) {
}
