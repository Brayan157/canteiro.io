package com.renovar.canteiro.io.identity.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyUserRepository {

    CompanyUser save(CompanyUser companyUser);

    Optional<CompanyUser> findByUserId(UUID userId);

    Optional<CompanyUser> findByUserIdAndCompanyId(UUID userId, UUID companyId);

    Page<CompanyUser> findByCompanyId(UUID companyId, Pageable pageable);
}
