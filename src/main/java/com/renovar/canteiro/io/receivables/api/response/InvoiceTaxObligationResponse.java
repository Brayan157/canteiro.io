package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.receivables.application.ReadInvoiceTaxesUseCase;
import com.renovar.canteiro.io.receivables.domain.TaxObligationStatus;
import com.renovar.canteiro.io.receivables.domain.TaxTreatment;
import com.renovar.canteiro.io.shared.api.json.CanonicalMoneySerializer;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Official persisted invoice tax, including cancelled history. No receivable/payable IDs, balances, payment state or fiscal access key. Currency is inherited from the invoice contract; GET never reapplies tax effects.",
        requiredProperties = {"id", "invoiceId", "contractId", "name", "amount", "treatment", "status", "currency", "createdAt", "updatedAt"})
public record InvoiceTaxObligationResponse(UUID id, UUID invoiceId, UUID contractId, String name,
        @Schema(types = {"string", "null"}) String description,
        @Schema(types = {"string", "null"}, format = "date", description = "Recorded due date, if any; mandatory on creation only for PAYABLE_BY_COMPANY.") LocalDate dueOn,
        @JsonSerialize(using = CanonicalMoneySerializer.class)
        @Schema(type = "string", format = "decimal", pattern = "^(0|[1-9][0-9]*)\\.[0-9]{2}$", example = "10.00") BigDecimal amount,
        TaxTreatment treatment, TaxObligationStatus status, CurrencyCode currency, Instant createdAt, Instant updatedAt) {
    public static InvoiceTaxObligationResponse from(ReadInvoiceTaxesUseCase.Entry entry) {
        var tax = entry.tax();
        return new InvoiceTaxObligationResponse(tax.getId(), tax.getInvoiceId(), tax.getContractId(), tax.getName(),
                tax.getDescription(), tax.getDueOn(), tax.getAmount(), tax.getTreatment(), tax.getStatus(), entry.currency(),
                tax.getCreatedAt(), tax.getUpdatedAt());
    }
}
