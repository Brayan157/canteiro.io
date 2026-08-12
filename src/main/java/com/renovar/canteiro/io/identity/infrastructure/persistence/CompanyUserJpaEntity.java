package com.renovar.canteiro.io.identity.infrastructure.persistence;

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
@Table(name = "company_user")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanyUserJpaEntity extends BaseJpaEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    public CompanyUserJpaEntity(UUID userId, UUID companyId) {
        this.userId = userId;
        this.companyId = companyId;
    }
}
