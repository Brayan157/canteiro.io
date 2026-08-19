package com.renovar.canteiro.io.contracts.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Provides non-service, measurement-originated adjustments that reduce a contract's net amount.
 */
public interface ContractMeasurementAdjustmentAmountProvider {

    BigDecimal approvedAmount(UUID companyId, UUID contractId);
}
