package com.renovar.canteiro.io.contracts.api.response;

import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import com.renovar.canteiro.io.contracts.domain.ContractPricingMode;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;

import java.time.LocalDate;
import java.util.UUID;

public record ContractResponse(
        UUID id,
        UUID workId,
        String contractIdentifier,
        ContractPricingMode pricingMode,
        String reference,
        String name,
        ContractStatus status,
        LocalDate startedOn,
        LocalDate expectedCompletionOn,
        LocalDate completedOn,
        CurrencyCode currency,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
        @io.swagger.v3.oas.annotations.media.Schema(types = {"boolean", "null"}, requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED,
                description = "Authoritative boolean on GET /api/v1/company/contracts/{contractId}: an active commercial ContractDiscount exists in the session tenant, even when its calculated effect is zero. Measurement adjustments alone do not make it true. Null in list and mutation responses means not evaluated; refresh the contract detail. Does not grant reversal permission or bypass approval/concurrency validation.")
        Boolean hasActiveContractDiscount
) {
}
