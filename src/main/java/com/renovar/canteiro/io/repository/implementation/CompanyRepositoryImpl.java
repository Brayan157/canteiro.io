package com.renovar.canteiro.io.repository.implementation;

import com.renovar.canteiro.io.dto.mapper.CompanyMapper;
import com.renovar.canteiro.io.dto.model.CompanyModel;
import com.renovar.canteiro.io.entity.Company;
import com.renovar.canteiro.io.repository.contract.CompanyRepository;
import com.renovar.canteiro.io.repository.jpa.CompanyJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CompanyRepositoryImpl implements CompanyRepository {
    private final CompanyJpaRepository companyJpaRepository;
    private final CompanyMapper companyMapper;
    @Override
    public CompanyModel save(CompanyModel company) {
        Company entity = companyMapper.toEntity(company);
        Company savedEntity = companyJpaRepository.save(entity);
        return companyMapper.toModel(savedEntity);
    }

    @Override
    public CompanyModel findById(UUID id) {
        return companyJpaRepository.findById(id)
                .map(companyMapper::toModel)
                .orElse(null);
    }

    @Override
    public List<CompanyModel> findAll() {
        return companyJpaRepository.findAll().stream()
                .map(companyMapper::toModel)
                .toList();
    }
}
