package com.renovar.canteiro.io.access.infrastructure.persistence;

import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.shared.infrastructure.persistence.jpa.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "permission")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PermissionJpaEntity extends BaseJpaEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "module", nullable = false, length = 40)
    private AccessModule module;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 40)
    private AccessAction action;

    @Column(name = "active", nullable = false)
    private boolean active;

    public PermissionJpaEntity(AccessModule module, AccessAction action, boolean active) {
        this.module = module;
        this.action = action;
        this.active = active;
    }
}
