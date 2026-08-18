package com.renovar.canteiro.io.customers.domain;

import java.util.List;
import java.util.UUID;

public interface FinalCustomerContactRepository {

    FinalCustomerContact save(FinalCustomerContact contact);

    List<FinalCustomerContact> findByFinalCustomerIdAndCompanyId(UUID finalCustomerId, UUID companyId);
}
