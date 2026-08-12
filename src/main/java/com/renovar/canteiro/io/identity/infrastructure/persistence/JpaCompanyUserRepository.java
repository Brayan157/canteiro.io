package com.renovar.canteiro.io.identity.infrastructure.persistence;

import com.renovar.canteiro.io.identity.domain.CompanyUser;
import com.renovar.canteiro.io.identity.domain.CompanyUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaCompanyUserRepository implements CompanyUserRepository {

    private final CompanyUserJpaRepository companyUserJpaRepository;
    private final CompanyUserPersistenceMapper companyUserPersistenceMapper;

    @Override
    public CompanyUser save(CompanyUser companyUser) {
        if (companyUser.getId() != null) {
            throw new IllegalStateException("Company user links are immutable");
        }
        return companyUserPersistenceMapper.toDomain(
                companyUserJpaRepository.save(companyUserPersistenceMapper.toJpaEntity(companyUser))
        );
    }

    @Override
    public Optional<CompanyUser> findByUserId(UUID userId) {
        return companyUserJpaRepository.findByUserId(userId).map(companyUserPersistenceMapper::toDomain);
    }

    @Override
    public Optional<CompanyUser> findByUserIdAndCompanyId(UUID userId, UUID companyId) {
        return companyUserJpaRepository.findByUserIdAndCompanyId(userId, companyId)
                .map(companyUserPersistenceMapper::toDomain);
    }

    @Override
    public Page<CompanyUser> findByCompanyId(UUID companyId, Pageable pageable) {
        return companyUserJpaRepository.findByCompanyId(companyId, pageable).map(companyUserPersistenceMapper::toDomain);
    }
}
