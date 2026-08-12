package com.renovar.canteiro.io.access.infrastructure.persistence;

import com.renovar.canteiro.io.access.domain.Role;
import com.renovar.canteiro.io.access.domain.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaRoleRepository implements RoleRepository {

    private final RoleJpaRepository roleJpaRepository;
    private final RolePersistenceMapper rolePersistenceMapper;

    @Override
    public Role save(Role role) {
        if (role.getId() == null) {
            return rolePersistenceMapper.toDomain(roleJpaRepository.save(rolePersistenceMapper.toJpaEntity(role)));
        }
        RoleJpaEntity entity = roleJpaRepository.findById(role.getId())
                .orElseThrow(() -> new IllegalStateException("Role must exist before it can be updated"));
        rolePersistenceMapper.updateJpaEntity(entity, role);
        return rolePersistenceMapper.toDomain(roleJpaRepository.save(entity));
    }

    @Override
    public Optional<Role> findByIdAndCompanyId(UUID roleId, UUID companyId) {
        return roleJpaRepository.findByIdAndCompanyId(roleId, companyId).map(rolePersistenceMapper::toDomain);
    }

    @Override
    public Page<Role> findByCompanyId(UUID companyId, Pageable pageable) {
        return roleJpaRepository.findByCompanyId(companyId, pageable).map(rolePersistenceMapper::toDomain);
    }
}
