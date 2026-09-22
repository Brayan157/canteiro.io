package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.receivables.domain.InvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InvoiceResponse(
        UUID id,
        UUID contractId,
        String number,
        String series,
        String accessKey,
        LocalDate issuedOn,
        BigDecimal totalAmount,
        InvoiceStatus status,
        List<InvoiceItemResponse> items
) {
}
