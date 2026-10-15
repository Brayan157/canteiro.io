package com.renovar.canteiro.io.measurements.domain;
import java.util.UUID;
public record MeasurementCompositionState(UUID continuousServiceId,UUID measurementVersionId,UUID measurementServiceId,
        UUID measurementContractCompositionId,String state,UUID changeRequestId){}

