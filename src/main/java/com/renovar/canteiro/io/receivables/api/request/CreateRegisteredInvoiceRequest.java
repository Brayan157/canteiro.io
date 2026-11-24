package com.renovar.canteiro.io.receivables.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateRegisteredInvoiceRequest(
        @NotNull UUID contractId,
        @NotBlank String number,
        String series,
        String accessKey,
        @NotNull LocalDate issuedOn,
        @NotEmpty List<@Valid InvoiceItemRequest> items,
        String justification,
        @tools.jackson.databind.annotation.JsonDeserialize(using=com.renovar.canteiro.io.shared.api.json.StrictDecimalStringDeserializer.class)
        @jakarta.validation.constraints.Pattern(regexp="^(0|[1-9][0-9]{0,16})[.][0-9]{2}$")
        @io.swagger.v3.oas.annotations.media.Schema(types={"string","null"},format="decimal",example="1000.00",
                description="Confirmed documentary commercial amount before withholding. XML-assisted clients must send the reviewed commercialAmountCandidate; manual legacy clients may omit. Backend compares with whole selected service groups and revalidates approval; no frontend sum/rateio. Not a signature/source binding.")
        String documentTotalAmount
) {
}
