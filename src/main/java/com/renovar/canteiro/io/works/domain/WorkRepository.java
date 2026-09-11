package com.renovar.canteiro.io.works.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface WorkRepository {
    Work save(Work work);
    Optional<Work> findByIdAndCompanyId(UUID id, UUID companyId);
    Page<Work> findByCompanyId(UUID companyId, Pageable pageable);
}
