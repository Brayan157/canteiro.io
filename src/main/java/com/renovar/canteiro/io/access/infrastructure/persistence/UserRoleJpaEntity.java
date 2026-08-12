package com.renovar.canteiro.io.access.infrastructure.persistence;

import com.renovar.canteiro.io.shared.infrastructure.persistence.jpa.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(name = "user_role")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserRoleJpaEntity extends BaseJpaEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "active", nullable = false)
    private boolean active;

    public UserRoleJpaEntity(UUID userId, UUID roleId, UUID companyId, boolean active) {
        this.userId = userId;
        this.roleId = roleId;
        this.companyId = companyId;
        this.active = active;
    }

    public void changeActive(boolean active) {
        this.active = active;
    }
}
