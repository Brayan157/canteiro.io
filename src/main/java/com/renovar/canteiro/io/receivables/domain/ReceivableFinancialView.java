package com.renovar.canteiro.io.receivables.domain;

/** A read filter, independent of the persisted business status. */
public enum ReceivableFinancialView {
    ALL, OUTSTANDING, RECEIVED
}
