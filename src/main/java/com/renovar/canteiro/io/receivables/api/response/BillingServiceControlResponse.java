package com.renovar.canteiro.io.receivables.api.response;

import java.math.BigDecimal;
import java.util.UUID;

public record BillingServiceControlResponse(UUID contractServiceId, String serviceName, BigDecimal serviceNetAmount,
                                            BigDecimal activeAllocatedAmount, BigDecimal availableAmount,
                                            BigDecimal issuedInInvoicesAmount) {
}
