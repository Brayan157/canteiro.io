package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.receivables.domain.ReceivableReadProjection;
import com.renovar.canteiro.io.receivables.domain.ReceivableStatus;
import com.renovar.canteiro.io.shared.api.json.CanonicalMoneySerializer;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Official title and current financialPosition, requiring RECEIVABLES.READ. originalAmount/status remain persisted face value/status. Financial totals are canonical and separate cash, credits, withholding and outstanding; no tax/settlement payload or measurement content. No cross-currency aggregation.",
        requiredProperties = {"id", "contractId", "contractName", "workId", "workName", "finalCustomerId", "finalCustomerName",
                "billingInstallmentId", "financialGroupId", "dueOn", "status", "currency", "originalAmount", "createdAt", "updatedAt", "financialPosition"})
public record ReceivableSummaryResponse(UUID id, @Schema(types = {"string", "null"}) String reference,
        UUID contractId, String contractName, @Schema(types = {"string", "null"}) String contractReference,
        @Schema(types = {"string", "null"}) String contractIdentifier,
        UUID workId, String workName, @Schema(types = {"string", "null"}) String workReference,
        UUID finalCustomerId, String finalCustomerName,
        UUID billingInstallmentId, @Schema(types = {"string", "null"}) String billingInstallmentReference,
        UUID financialGroupId, @Schema(types = {"string", "null"}, format = "uuid") UUID renegotiationSourceReceivableId,
        @Schema(types = {"string", "null"}) String renegotiationSourceReference,
        @Schema(types = {"string", "null"}, format = "date") LocalDate issuedOn, LocalDate dueOn,
        @Schema(description = "Persisted official status; never updated on GET. Use financialPosition for cash/adjustment position; dueOn remains a business date and this status is not a derived overdue indicator.") ReceivableStatus status,
        CurrencyCode currency,
        @JsonSerialize(using = CanonicalMoneySerializer.class)
        @Schema(type = "string", format = "decimal", pattern = "^(0|[1-9][0-9]*)\\.[0-9]{2}$", example = "99.90") BigDecimal originalAmount,
        Instant createdAt, Instant updatedAt, ReceivableFinancialPositionResponse financialPosition) {
    public static ReceivableSummaryResponse from(ReceivableReadProjection v) {
        return new ReceivableSummaryResponse(v.id(), v.reference(), v.contractId(), v.contractName(), v.contractReference(),
                v.contractIdentifier(), v.workId(), v.workName(), v.workReference(), v.finalCustomerId(), v.finalCustomerName(),
                v.billingInstallmentId(), v.billingInstallmentReference(), v.financialGroupId(), v.renegotiationSourceReceivableId(),
                v.renegotiationSourceReference(), v.issuedOn(), v.dueOn(), v.status(), v.currency(), v.originalAmount(), v.createdAt(), v.updatedAt(),
                ReceivableFinancialPositionResponse.from(v.financialPosition()));
    }
}
