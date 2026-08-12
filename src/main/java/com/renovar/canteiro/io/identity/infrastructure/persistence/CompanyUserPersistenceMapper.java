package com.renovar.canteiro.io.identity.infrastructure.persistence;

import com.renovar.canteiro.io.identity.domain.CompanyUser;
import org.springframework.stereotype.Component;

@Component
public class CompanyUserPersistenceMapper {

    public CompanyUserJpaEntity toJpaEntity(CompanyUser companyUser) {
        return new CompanyUserJpaEntity(companyUser.getUserId(), companyUser.getCompanyId());
    }

    public CompanyUser toDomain(CompanyUserJpaEntity entity) {
        return CompanyUser.rehydrate(
                entity.getId(),
                entity.getUserId(),
                entity.getCompanyId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
