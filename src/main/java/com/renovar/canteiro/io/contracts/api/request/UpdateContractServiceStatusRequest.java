package com.renovar.canteiro.io.contracts.api.request;

import com.renovar.canteiro.io.contracts.domain.ContractServiceStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateContractServiceStatusRequest(@NotNull ContractServiceStatus status, @Size(max = 1000) String justification) { }
