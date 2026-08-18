package com.renovar.canteiro.io.customers.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface FinalCustomerRepository {

    FinalCustomer save(FinalCustomer finalCustomer);

    Optional<FinalCustomer> findByIdAndCompanyId(UUID id, UUID companyId);

    Optional<FinalCustomer> findByCompanyIdAndDocument(UUID companyId, String document);

    Page<FinalCustomer> findByCompanyId(UUID companyId, Pageable pageable);
}
