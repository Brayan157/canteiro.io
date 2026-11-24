package com.renovar.canteiro.io.contracts.application;

import java.math.BigDecimal;
import java.util.UUID;

public interface ContractDiscountRestrictionProvider {
    void lockInputs(UUID companyId, UUID contractId);
    String restriction(UUID companyId, UUID contractId, BigDecimal proposedNetAmount, boolean grantingDiscount);
}
