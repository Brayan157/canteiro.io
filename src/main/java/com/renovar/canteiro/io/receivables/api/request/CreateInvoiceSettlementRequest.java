package com.renovar.canteiro.io.receivables.api.request;

import com.renovar.canteiro.io.receivables.application.CreateInvoiceSettlementCommand;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementPaymentMethod;
import com.renovar.canteiro.io.shared.api.json.StrictDecimalStringDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Manual receipt with exact invoice-item origins. No automatic distribution. A pending proposal never reserves capacity.")
public record CreateInvoiceSettlementRequest(@NotNull UUID operationId,
        @NotNull @Schema(description = "Actual business date, not later than the official current UTC date.") LocalDate receivedOn,
        @NotBlank @Pattern(regexp = "^(0|[1-9][0-9]*)\\.[0-9]{2}$") @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 17, fraction = 2)
        @JsonDeserialize(using = StrictDecimalStringDeserializer.class)
        @Schema(type = "string", format = "decimal", example = "100.00") String amount,
        @NotNull ReceivableSettlementPaymentMethod paymentMethod, @Size(max = 1000) String note,
        @Size(max = 1000) String justification, @NotEmpty @Size(max = 100) List<@NotNull @Valid Line> items) {
    @Schema(name = "InvoiceSettlementLineRequest")
    public record Line(@NotNull UUID invoiceId, @NotNull UUID invoiceItemId, @NotNull UUID billingAllocationId,
            @Schema(types = {"string", "null"}, format = "uuid", description = "Exact composition from options; null only when the allocation has no measurement origin.") UUID measurementContractCompositionId,
            @NotBlank @Pattern(regexp = "^(0|[1-9][0-9]*)\\.[0-9]{2}$") @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 17, fraction = 2)
            @JsonDeserialize(using = StrictDecimalStringDeserializer.class)
            @Schema(type = "string", format = "decimal", example = "100.00") String amount) { }
    public CreateInvoiceSettlementCommand toCommand(UUID receivableId) {
        return new CreateInvoiceSettlementCommand(operationId, receivableId, receivedOn, new BigDecimal(amount), paymentMethod, note, justification,
                items.stream().map(line -> new CreateInvoiceSettlementCommand.Line(line.invoiceId(), line.invoiceItemId(), line.billingAllocationId(),
                        line.measurementContractCompositionId(), new BigDecimal(line.amount()))).toList());
    }
}
