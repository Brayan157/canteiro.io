package com.renovar.canteiro.io.receivables.application;

import java.util.Optional;
import java.util.UUID;

public interface InvoiceXmlFiscalSnapshotRepository {
    Optional<InvoiceXmlFiscalSnapshot> find(UUID companyId, UUID invoiceId);

    boolean insert(InvoiceXmlFiscalSnapshot snapshot, String accessKeyHash);
}
