package com.renovar.canteiro.io.contracts.application;

import java.math.BigDecimal;
import java.util.UUID;

public interface ApprovedContractServiceBillingAmountProvider {

    BigDecimal approvedNetAmount(UUID companyId, UUID contractServiceId);
}
