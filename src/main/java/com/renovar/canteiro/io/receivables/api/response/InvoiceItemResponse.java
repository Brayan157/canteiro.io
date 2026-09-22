package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.receivables.domain.InvoiceItemStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record InvoiceItemResponse(
        UUID id,
        UUID billingAllocationId,
        UUID contractServiceId,
        String description,
        BigDecimal allocatedAmount,
        InvoiceItemStatus status
) {
}
