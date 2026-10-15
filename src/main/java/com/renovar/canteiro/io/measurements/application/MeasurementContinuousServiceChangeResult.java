package com.renovar.canteiro.io.measurements.application;
import com.renovar.canteiro.io.measurements.domain.MeasurementContinuousService;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
public record MeasurementContinuousServiceChangeResult(MeasurementContinuousService service,ChangeRequest changeRequest,ChangeAuthorizationMode authorizationMode) {}

