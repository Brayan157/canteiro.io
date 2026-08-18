package com.renovar.canteiro.io.contracts.infrastructure.billing;

import com.renovar.canteiro.io.contracts.application.ApprovedContractBillingAmountProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class PreBillingApprovedAmountProvider implements ApprovedContractBillingAmountProvider {

    @Override
    public BigDecimal approvedNetAmount(UUID companyId, UUID contractId) {
        return BigDecimal.ZERO.setScale(2);
    }
}
