package com.renovar.canteiro.io.receivables.domain;

public final class InvoiceFiscalIdentityConflict extends RuntimeException {
    public InvoiceFiscalIdentityConflict(){super("Invoice fiscal identity is already registered in this company");}
}
