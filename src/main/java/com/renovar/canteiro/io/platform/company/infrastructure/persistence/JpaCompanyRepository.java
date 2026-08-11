package com.renovar.canteiro.io.platform.company.infrastructure.persistence;

import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaCompanyRepository implements CompanyRepository {

    private final CompanyJpaRepository companyJpaRepository;
    private final CompanyPersistenceMapper companyPersistenceMapper;

    @Override
    public Company save(Company company) {
        if (company.getId() == null) {
            return companyPersistenceMapper.toDomain(companyJpaRepository.save(companyPersistenceMapper.toJpaEntity(company)));
        }

        CompanyJpaEntity entity = companyJpaRepository.findById(company.getId())
                .orElseThrow(() -> new IllegalStateException("Company must exist before it can be updated"));
        companyPersistenceMapper.updateJpaEntity(entity, company);
        return companyPersistenceMapper.toDomain(companyJpaRepository.save(entity));
    }

    @Override
    public Optional<Company> findById(UUID id) {
        return companyJpaRepository.findById(id).map(companyPersistenceMapper::toDomain);
    }

    @Override
    public List<Company> findAll() {
        return companyJpaRepository.findAll().stream().map(companyPersistenceMapper::toDomain).toList();
    }
}
