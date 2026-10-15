package com.renovar.canteiro.io.receivables.domain;

import java.math.BigDecimal;

public record ContractBillingBalance(
        BigDecimal serviceSubtotal,
        BigDecimal contractDiscountAmount,
        BigDecimal contractNetAmount,
        BigDecimal activeAllocatedAmount,
        BigDecimal availableAmount
) {
}
