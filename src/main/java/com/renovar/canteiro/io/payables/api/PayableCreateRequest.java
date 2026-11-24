package com.renovar.canteiro.io.payables.api;

import com.renovar.canteiro.io.payables.application.PayableCreateCommand;
import com.renovar.canteiro.io.payables.domain.PayableFinancialScope;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import com.renovar.canteiro.io.shared.api.json.StrictDecimalStringDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import tools.jackson.databind.annotation.JsonDeserialize;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description="Creates 1..100 individual titles atomically in one server-generated financial group. No automatic installments, division, payment or bank transfer. Tenant comes only from the session.")
public record PayableCreateRequest(@NotNull UUID operationId,
        @NotNull @Schema(description="Explicit required scope. WORK requires workId; optional contractId must belong to that work. COMPANY forbids both links. Category never determines scope.") PayableFinancialScope financialScope,
        UUID workId,UUID contractId,UUID supplierId,UUID financialCategoryId,
        @Schema(description="Contract currency when linked: omitted derives it, a different value returns 409. Otherwise optional, defaults BRL. Immutable; no conversion.") CurrencyCode currency,
        @NotEmpty @Size(max=100) List<@NotNull @Valid Title> titles,@Size(max=1000) String justification) {
    @Schema(name="PayableTitleCreateRequest")
    public record Title(@NotBlank @Size(max=100) String reference,@Size(max=1000) String description,
            @Schema(description="Optional issue date; never inferred from due date.") LocalDate issuedOn,
            @NotNull @Schema(description="Business date; past due dates are allowed, without automatic payment or status mutation.") LocalDate dueOn,
            @NotBlank @Pattern(regexp="^(0|[1-9][0-9]*)\\.[0-9]{2}$") @DecimalMin(value="0.00",inclusive=false) @Digits(integer=17,fraction=2)
            @JsonDeserialize(using=StrictDecimalStringDeserializer.class)
            @Schema(type="string",format="decimal",pattern="^(0|[1-9][0-9]*)\\.[0-9]{2}$",example="150.00",description="Positive exact money, two decimal places, no numeric JSON, exponent, symbol or comma.") String originalAmount) { }
    public PayableCreateCommand toCommand(){return new PayableCreateCommand(operationId,financialScope,workId,contractId,supplierId,financialCategoryId,currency,
            titles.stream().map(t->new PayableCreateCommand.Title(t.reference(),t.description(),t.issuedOn(),t.dueOn(),new BigDecimal(t.originalAmount()))).toList(),justification);}
}
