package com.renovar.canteiro.io.platform.company.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository {

    Company save(Company company);

    Optional<Company> findById(UUID id);

    Optional<Company> findByDocument(String document);

    Optional<Company> findByEmail(String email);

    List<Company> findAll();
}
