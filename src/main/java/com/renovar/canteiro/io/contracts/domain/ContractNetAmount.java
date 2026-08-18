package com.renovar.canteiro.io.contracts.domain;

import java.math.BigDecimal;

public record ContractNetAmount(
        BigDecimal serviceSubtotal,
        BigDecimal contractDiscountAmount,
        BigDecimal netAmount
) {
}
