package com.renovar.canteiro.io.customers.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinalCustomerAddressRepository {

    FinalCustomerAddress save(FinalCustomerAddress address);

    List<FinalCustomerAddress> findByFinalCustomerIdAndCompanyId(UUID finalCustomerId, UUID companyId);

    Optional<FinalCustomerAddress> findByIdAndFinalCustomerIdAndCompanyId(UUID id, UUID finalCustomerId, UUID companyId);
}
