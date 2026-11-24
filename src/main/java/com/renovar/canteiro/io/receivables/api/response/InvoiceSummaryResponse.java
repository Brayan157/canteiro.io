package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.receivables.domain.InvoiceStatus;
import com.renovar.canteiro.io.receivables.domain.InvoiceSummary;
import com.renovar.canteiro.io.receivables.domain.InvoiceReceiptPosition;
import com.renovar.canteiro.io.receivables.application.ReadInvoicesUseCase;
import com.renovar.canteiro.io.shared.api.json.CanonicalMoneySerializer;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Official invoice header, including cancelled history. Contract labels reflect its current safe identification. No fiscal access key or storage data.",
        requiredProperties = {"id", "contractId", "contractName", "number", "issuedOn", "status", "currency", "totalAmount", "createdAt", "updatedAt", "receiptPosition"})
public record InvoiceSummaryResponse(UUID id, UUID contractId, String contractName,
        @Schema(types = {"string", "null"}) String contractReference,
        @Schema(types = {"string", "null"}) String contractIdentifier,
        String number, @Schema(types = {"string", "null"}) String series, LocalDate issuedOn,
        InvoiceStatus status, CurrencyCode currency,
        @JsonSerialize(using = CanonicalMoneySerializer.class)
        @Schema(type = "string", format = "decimal", pattern = "^-?(0|[1-9][0-9]*)\\.[0-9]{2}$", example = "99.90") BigDecimal totalAmount,
        Instant createdAt, Instant updatedAt, InvoiceReceiptPositionResponse receiptPosition) {
    public static InvoiceSummaryResponse from(ReadInvoicesUseCase.Summary value) {
        return from(value.invoice(), value.receiptPosition());
    }

    public static InvoiceSummaryResponse from(InvoiceSummary value, InvoiceReceiptPosition receiptPosition) {
        return new InvoiceSummaryResponse(value.id(), value.contractId(), value.contractName(), value.contractReference(),
                value.contractIdentifier(), value.number(), value.series(), value.issuedOn(), value.status(), value.currency(),
                value.totalAmount(), value.createdAt(), value.updatedAt(), InvoiceReceiptPositionResponse.from(receiptPosition));
    }
}
