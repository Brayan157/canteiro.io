package com.renovar.canteiro.io.repository.contract;

import com.renovar.canteiro.io.dto.model.CompanyModel;

import java.util.List;
import java.util.UUID;

public interface CompanyRepository {
    CompanyModel save(CompanyModel company);
    CompanyModel findById(UUID id);
    List<CompanyModel> findAll();
}
