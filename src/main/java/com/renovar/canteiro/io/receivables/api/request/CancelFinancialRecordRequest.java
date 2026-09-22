package com.renovar.canteiro.io.receivables.api.request;

import jakarta.validation.constraints.NotBlank;

public record CancelFinancialRecordRequest(@NotBlank String reason, String justification) {
}
