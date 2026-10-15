package com.renovar.canteiro.io.measurements.api.request;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
public record AssignMeasurementItemServiceRequest(@NotNull UUID continuousServiceId,@Size(max=1000) String justification){}

