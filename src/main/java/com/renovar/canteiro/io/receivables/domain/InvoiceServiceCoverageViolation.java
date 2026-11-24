package com.renovar.canteiro.io.receivables.domain;

public final class InvoiceServiceCoverageViolation extends IllegalStateException {
    private final String reason;
    public InvoiceServiceCoverageViolation(String reason){
        super("INVOICE_DOCUMENT_TOTAL_MISMATCH".equals(reason)?"Confirmed document amount differs from the whole selected service groups":"Invoice documentation requires complete, proven service groups of the billing installment");this.reason=reason;
    }
    public String reason(){return reason;}
}
