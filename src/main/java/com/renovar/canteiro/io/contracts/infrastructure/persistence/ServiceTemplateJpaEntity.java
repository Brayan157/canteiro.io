package com.renovar.canteiro.io.contracts.infrastructure.persistence;

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
@Table(name = "service_template")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ServiceTemplateJpaEntity extends BaseJpaEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active;

    ServiceTemplateJpaEntity(UUID companyId, String name, String description, boolean active) {
        this.companyId = companyId;
        this.name = name;
        this.description = description;
        this.active = active;
    }
}
