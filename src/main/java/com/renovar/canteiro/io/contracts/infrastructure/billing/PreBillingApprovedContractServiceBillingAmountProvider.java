package com.renovar.canteiro.io.contracts.infrastructure.billing;

import com.renovar.canteiro.io.contracts.application.ApprovedContractServiceBillingAmountProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class PreBillingApprovedContractServiceBillingAmountProvider
        implements ApprovedContractServiceBillingAmountProvider {

    @Override
    public BigDecimal approvedNetAmount(UUID companyId, UUID contractServiceId) {
        return BigDecimal.ZERO.setScale(2);
    }
}
