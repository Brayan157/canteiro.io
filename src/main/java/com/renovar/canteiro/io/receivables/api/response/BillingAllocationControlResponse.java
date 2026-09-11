package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.receivables.domain.BillingAllocationStatus;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationType;
import com.renovar.canteiro.io.receivables.domain.BillingInstallmentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BillingAllocationControlResponse(UUID allocationId, UUID billingInstallmentId, String installmentReference,
                                               LocalDate installmentDueOn, BillingInstallmentStatus installmentStatus,
                                               UUID contractServiceId, String serviceName, BillingAllocationType type,
                                               BillingAllocationStatus status, BigDecimal allocatedAmount,
                                               BigDecimal issuedInInvoicesAmount, BigDecimal availableInvoiceAmount) {
}
