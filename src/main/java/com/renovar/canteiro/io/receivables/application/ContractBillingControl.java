package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.contracts.domain.Contract;
import com.renovar.canteiro.io.receivables.domain.ContractBillingBalance;

import java.util.List;

public record ContractBillingControl(Contract contract, ContractBillingBalance balance,
                                     List<BillingServiceControl> services,
                                     List<BillingAllocationControl> allocations) {
}
