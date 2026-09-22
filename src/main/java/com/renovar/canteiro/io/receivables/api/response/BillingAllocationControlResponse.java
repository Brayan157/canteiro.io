package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.receivables.domain.BillingAllocationStatus;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationType;
import com.renovar.canteiro.io.receivables.domain.BillingInstallmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.UUID;

public record BillingAllocationControlResponse(UUID allocationId, UUID billingInstallmentId, String installmentReference,
                                               LocalDate installmentDueOn, BillingInstallmentStatus installmentStatus,
                                               UUID contractServiceId, String serviceName, BillingAllocationType type,
                                               BillingAllocationStatus status,
                                               @Schema(type = "string", format = "decimal", example = "99.90", pattern = "^-?\\d+\\.\\d{2}$") String allocatedAmount,
                                               @Schema(type = "string", format = "decimal", example = "99.90", pattern = "^-?\\d+\\.\\d{2}$") String registeredInvoiceAmount,
                                               @Schema(type = "string", format = "decimal", example = "99.90", pattern = "^-?\\d+\\.\\d{2}$") String availableInvoiceAmount) {
}
