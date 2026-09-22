package com.renovar.canteiro.io.receivables.infrastructure.contracts;

import com.renovar.canteiro.io.contracts.application.ApprovedContractBillingAmountProvider;
import com.renovar.canteiro.io.receivables.application.CalculateContractBillingBalanceUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BillingAllocationApprovedContractBillingAmountProvider implements ApprovedContractBillingAmountProvider {

    private final CalculateContractBillingBalanceUseCase calculateContractBillingBalanceUseCase;

    @Override
    public BigDecimal approvedNetAmount(UUID companyId, UUID contractId) {
        return calculateContractBillingBalanceUseCase.calculate(companyId, contractId).activeAllocatedAmount();
    }
}
