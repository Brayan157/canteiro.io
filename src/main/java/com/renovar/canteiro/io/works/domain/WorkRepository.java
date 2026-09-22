package com.renovar.canteiro.io.works.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.renovar.canteiro.io.works.application.WorkFilter;

import java.util.Optional;
import java.util.UUID;

public interface WorkRepository {
    Work save(Work work);
    Optional<Work> findByIdAndCompanyId(UUID id, UUID companyId);
    Page<Work> findByCompanyId(UUID companyId, WorkFilter filter, Pageable pageable);
    default Page<Work> findByCompanyId(UUID companyId, Pageable pageable) {
        return findByCompanyId(companyId, new WorkFilter(null, null, null, null, null, null, null, null, null, null, null), pageable);
    }
}
