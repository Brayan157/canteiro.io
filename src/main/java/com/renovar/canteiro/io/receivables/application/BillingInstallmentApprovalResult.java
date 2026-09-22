package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.receivables.domain.BillingInstallment;
import com.renovar.canteiro.io.receivables.domain.BillingAllocation;
import com.renovar.canteiro.io.receivables.domain.Receivable;

import java.util.List;

public record BillingInstallmentApprovalResult(
        BillingInstallment billingInstallment,
        List<BillingAllocation> allocations,
        Receivable receivable,
        ChangeRequest changeRequest,
        ChangeAuthorizationMode mode
) {
}
