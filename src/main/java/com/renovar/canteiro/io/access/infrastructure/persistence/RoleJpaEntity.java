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
@Table(name = "access_role")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoleJpaEntity extends BaseJpaEntity {

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active;

    public RoleJpaEntity(UUID companyId, String name, String description, boolean active) {
        this.companyId = companyId;
        this.name = name;
        this.description = description;
        this.active = active;
    }

    public void update(String name, String description, boolean active) {
        this.name = name;
        this.description = description;
        this.active = active;
    }
}
