package com.renovar.canteiro.io.receivables.application;

import java.math.BigDecimal;
import java.util.UUID;

public record InvoiceItemCommand(
        UUID billingAllocationId,
        String description,
        BigDecimal allocatedAmount
) {
}
