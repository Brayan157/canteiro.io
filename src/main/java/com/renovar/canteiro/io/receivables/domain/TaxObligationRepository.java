package com.renovar.canteiro.io.receivables.domain;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface TaxObligationRepository {

    TaxObligation save(TaxObligation taxObligation);

    Optional<TaxObligation> findByIdAndCompanyId(UUID id, UUID companyId);

    BigDecimal registeredWithheldAmountByReceivableIdAndCompanyId(UUID receivableId, UUID companyId);

    List<TaxObligation> findByInvoiceIdAndCompanyId(UUID invoiceId, UUID companyId);

    TaxObligation updateStatus(TaxObligation taxObligation);
}
