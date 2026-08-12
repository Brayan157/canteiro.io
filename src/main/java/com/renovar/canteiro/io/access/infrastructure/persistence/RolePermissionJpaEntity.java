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
@Table(name = "role_permission")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RolePermissionJpaEntity extends BaseJpaEntity {

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "permission_id", nullable = false)
    private UUID permissionId;

    @Column(name = "active", nullable = false)
    private boolean active;

    public RolePermissionJpaEntity(UUID roleId, UUID permissionId, boolean active) {
        this.roleId = roleId;
        this.permissionId = permissionId;
        this.active = active;
    }

    public void changeActive(boolean active) {
        this.active = active;
    }
}
