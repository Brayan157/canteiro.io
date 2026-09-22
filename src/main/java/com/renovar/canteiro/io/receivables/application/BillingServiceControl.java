package com.renovar.canteiro.io.receivables.application;

import java.math.BigDecimal;
import java.util.UUID;

public record BillingServiceControl(UUID contractServiceId, String serviceName, BigDecimal serviceNetAmount,
                                    BigDecimal activeAllocatedAmount, BigDecimal availableAmount,
                                    BigDecimal issuedInInvoicesAmount) {
}
