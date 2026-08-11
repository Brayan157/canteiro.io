package com.renovar.canteiro.io.platform.company.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository {

    Company save(Company company);

    Optional<Company> findById(UUID id);

    List<Company> findAll();
}
