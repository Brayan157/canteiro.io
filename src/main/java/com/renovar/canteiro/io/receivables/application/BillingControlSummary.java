package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.contracts.domain.Contract;
import com.renovar.canteiro.io.receivables.domain.ContractBillingBalance;

public record BillingControlSummary(Contract contract, ContractBillingBalance balance) {
}
