package com.renovar.canteiro.io.receivables.domain;

import java.util.UUID;

/** Minimal current commercial identification, only for contracts represented by official invoices. */
public record InvoiceContractSummary(UUID contractId, String contractName, String contractReference,
        String contractIdentifier) { }
