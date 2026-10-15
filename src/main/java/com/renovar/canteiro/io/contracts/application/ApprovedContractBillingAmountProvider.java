package com.renovar.canteiro.io.contracts.application;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.Map;

public interface ApprovedContractBillingAmountProvider {

    BigDecimal approvedNetAmount(UUID companyId, UUID contractId);

    Map<UUID, BigDecimal> approvedServiceAmounts(UUID companyId, UUID contractId);

    Map<UUID, BigDecimal> approvedBaseServiceAmounts(UUID companyId, UUID contractId);

    BigDecimal approvedDiscountAmount(UUID companyId, UUID contractId);
}
