package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementHistory;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementEntryType;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementPaymentMethod;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementStatus;
import com.renovar.canteiro.io.shared.api.json.CanonicalMoneySerializer;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Official historical cash entry, not a balance or permission to reverse. SETTLEMENT is positive; REVERSAL is negative. Original status is not rewritten after reversal; follow exact reversal IDs, never infer by amount/date/name. No note, bank/fiscal data, attribution contents or audit payload.",
        requiredProperties = {"id", "receivableId", "contractId", "displayLabel", "receivedOn", "amount", "entryType", "paymentMethod", "status", "currency", "createdAt", "updatedAt"})
public record ReceivableSettlementHistoryResponse(UUID id, UUID receivableId,
        @Schema(types = {"string", "null"}) String receivableReference, UUID contractId,
        @Schema(description = "Controlled human label: Recebimento or Estorno de recebimento. Use with business date, payment method and createdAt for readable selection, id only as opaque selection key.") String displayLabel,
        @Schema(description = "Business date of this entry: receipt date for SETTLEMENT, reversal date for REVERSAL.") LocalDate receivedOn,
        @JsonSerialize(using = CanonicalMoneySerializer.class)
        @Schema(type = "string", format = "decimal", pattern = "^-?(0|[1-9][0-9]*)\\.[0-9]{2}$", example = "-25.00",
                description = "Persisted signed amount, positive receipt or negative reversal. Never absolute value, balance or frontend total.") BigDecimal amount,
        ReceivableSettlementEntryType entryType,
        @Schema(types = {"string", "null"}, format = "uuid", description = "Exact original receipt of a reversal, same title/contract/tenant. Null on original receipts.") UUID reversalOfSettlementId,
        @Schema(types = {"string", "null"}, format = "uuid", description = "Exact existing reversal of an original receipt, even if filtered out of this page. Null when no reversal exists or this row is a reversal. Not an authorization flag.") UUID reversedBySettlementId,
        ReceivableSettlementPaymentMethod paymentMethod,
        @Schema(description = "Persisted CONFIRMED or CANCELLED. A reversed original may remain CONFIRMED; it is not relabelled or removed.") ReceivableSettlementStatus status,
        CurrencyCode currency,
        @Schema(description = "Persisted UTC registration instant; distinguish from the business date. Not truncated to a date.") Instant createdAt,
        Instant updatedAt) {
    public static ReceivableSettlementHistoryResponse from(ReceivableSettlementHistory v) {
        return new ReceivableSettlementHistoryResponse(v.id(), v.receivableId(), v.receivableReference(), v.contractId(),
                v.entryType() == ReceivableSettlementEntryType.SETTLEMENT ? "Recebimento" : "Estorno de recebimento",
                v.receivedOn(), v.amount(), v.entryType(), v.reversalOfSettlementId(), v.reversedBySettlementId(),
                v.paymentMethod(), v.status(), v.currency(), v.createdAt(), v.updatedAt());
    }
}
