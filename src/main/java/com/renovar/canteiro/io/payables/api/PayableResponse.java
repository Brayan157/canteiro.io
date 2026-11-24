package com.renovar.canteiro.io.payables.api;

import com.renovar.canteiro.io.payables.domain.PayableFinancialScope;
import com.renovar.canteiro.io.payables.domain.PayableStatus;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import com.renovar.canteiro.io.shared.api.json.CanonicalMoneySerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

@Schema(description="Official individual payable. No manual balance, settlement or calculated payment is invented. Reference labels are safe server projections from this tenant.")
public record PayableResponse(UUID id,UUID financialGroupId,PayableFinancialScope financialScope,
        @Schema(types={"string","null"},format="uuid") UUID workId,@Schema(types={"string","null"},format="uuid") UUID contractId,
        @Schema(types={"string","null"},format="uuid") UUID supplierId,@Schema(types={"string","null"},format="uuid") UUID financialCategoryId,
        @Schema(types={"string","null"},format="uuid") UUID taxObligationId,@Schema(types={"string","null"}) String reference,
        @Schema(types={"string","null"}) String description,@Schema(types={"string","null"},format="date") LocalDate issuedOn,LocalDate dueOn,
        CurrencyCode currency,@JsonSerialize(using=CanonicalMoneySerializer.class)
        @Schema(type="string",format="decimal",pattern="^(0|[1-9][0-9]*)\\.[0-9]{2}$",example="150.00") BigDecimal originalAmount,
        PayableStatus status,@Schema(description="Derived as of today UTC for OPEN/PARTIALLY_PAID/OVERDUE. Does not write status during a GET.") boolean overdue,
        @Schema(description="Optimistic persistence version of the payable.") long version,
        Instant createdAt,Instant updatedAt,
        @Schema(implementation=ReferenceResponse.class) ReferenceResponse work,
        @Schema(implementation=ReferenceResponse.class) ReferenceResponse contract,
        @Schema(implementation=ReferenceResponse.class) ReferenceResponse supplier,
        @Schema(implementation=ReferenceResponse.class) ReferenceResponse financialCategory,
        PayableBalanceResponse balance) {
    @Schema(name="PayableReferenceResponse",description="Minimal safe label; no document, address, bank or contact details.")
    public record ReferenceResponse(UUID id,String displayName,@Schema(types={"string","null"}) String reference) { }
}
