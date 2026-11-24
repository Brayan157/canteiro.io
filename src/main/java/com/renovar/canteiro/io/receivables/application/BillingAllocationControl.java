package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.receivables.domain.BillingAllocation;
import com.renovar.canteiro.io.receivables.domain.BillingInstallment;
import com.renovar.canteiro.io.receivables.domain.InvoiceAllocationBalance;

public record BillingAllocationControl(BillingAllocation allocation, BillingInstallment installment,
                                       String serviceName, InvoiceAllocationBalance invoiceBalance,
                                       com.renovar.canteiro.io.receivables.domain.ReceivableFinancialPosition receivableFinancialPosition) {
    public com.renovar.canteiro.io.receivables.domain.ReceivableFinancialPosition.Status receivableFinancialStatus() {
        return receivableFinancialPosition == null ? null : receivableFinancialPosition.status();
    }

    public Boolean receivableReceivedWithWithholding() {
        return receivableFinancialPosition == null ? null : receivableFinancialPosition.receivedWithWithholding();
    }
}
