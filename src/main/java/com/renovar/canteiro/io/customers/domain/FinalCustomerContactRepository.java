package com.renovar.canteiro.io.customers.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinalCustomerContactRepository {

    FinalCustomerContact save(FinalCustomerContact contact);

    List<FinalCustomerContact> findByFinalCustomerIdAndCompanyId(UUID finalCustomerId, UUID companyId);

    Optional<FinalCustomerContact> findByIdAndFinalCustomerIdAndCompanyId(UUID id, UUID finalCustomerId, UUID companyId);
}
