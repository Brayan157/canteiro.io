package com.renovar.canteiro.io.access.infrastructure.persistence;

import com.renovar.canteiro.io.access.domain.UserRole;
import com.renovar.canteiro.io.access.domain.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaUserRoleRepository implements UserRoleRepository {

    private final UserRoleJpaRepository userRoleJpaRepository;
    private final UserRolePersistenceMapper userRolePersistenceMapper;

    @Override
    public UserRole save(UserRole userRole) {
        if (userRole.getId() == null) {
            return userRolePersistenceMapper.toDomain(
                    userRoleJpaRepository.save(userRolePersistenceMapper.toJpaEntity(userRole))
            );
        }
        UserRoleJpaEntity entity = userRoleJpaRepository.findById(userRole.getId())
                .orElseThrow(() -> new IllegalStateException("User role must exist before it can be updated"));
        userRolePersistenceMapper.updateJpaEntity(entity, userRole);
        return userRolePersistenceMapper.toDomain(userRoleJpaRepository.save(entity));
    }

    @Override
    public List<UserRole> findByUserIdAndCompanyId(UUID userId, UUID companyId) {
        return userRoleJpaRepository.findByUserIdAndCompanyId(userId, companyId).stream()
                .map(userRolePersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<UserRole> findByUserIdAndRoleIdAndCompanyId(UUID userId, UUID roleId, UUID companyId) {
        return userRoleJpaRepository.findByUserIdAndRoleIdAndCompanyId(userId, roleId, companyId)
                .map(userRolePersistenceMapper::toDomain);
    }
}
