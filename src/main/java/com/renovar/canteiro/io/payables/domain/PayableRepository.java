package com.renovar.canteiro.io.payables.domain;

import java.util.Optional;
import java.util.UUID;

public interface PayableRepository {

    Payable save(Payable payable);

    Optional<Payable> findByTaxObligationIdAndCompanyId(UUID taxObligationId, UUID companyId);

    Payable updateStatus(Payable payable);
}
