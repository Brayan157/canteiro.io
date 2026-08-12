package com.renovar.canteiro.io.access.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository {

    Role save(Role role);

    Optional<Role> findByIdAndCompanyId(UUID roleId, UUID companyId);

    Page<Role> findByCompanyId(UUID companyId, Pageable pageable);
}
