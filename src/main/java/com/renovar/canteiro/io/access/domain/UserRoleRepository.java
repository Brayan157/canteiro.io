package com.renovar.canteiro.io.access.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRoleRepository {

    UserRole save(UserRole userRole);

    List<UserRole> findByUserIdAndCompanyId(UUID userId, UUID companyId);

    Optional<UserRole> findByUserIdAndRoleIdAndCompanyId(UUID userId, UUID roleId, UUID companyId);
}
