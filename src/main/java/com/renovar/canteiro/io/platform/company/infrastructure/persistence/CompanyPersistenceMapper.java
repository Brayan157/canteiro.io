package com.renovar.canteiro.io.platform.company.infrastructure.persistence;

import com.renovar.canteiro.io.platform.company.domain.Company;
import org.springframework.stereotype.Component;

@Component
class CompanyPersistenceMapper {

    Company toDomain(CompanyJpaEntity entity) {
        return Company.rehydrate(
                entity.getId(), entity.getCorporateName(), entity.getTradeName(), entity.getDocument(), entity.getEmail(),
                entity.getPhone(), entity.getAddress(), entity.getLogo(), entity.isActive(),
                entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }

    CompanyJpaEntity toJpaEntity(Company company) {
        return new CompanyJpaEntity(
                company.getCorporateName(), company.getTradeName(), company.getDocument(), company.getEmail(),
                company.getPhone(), company.getAddress(), company.getLogo(), company.isActive()
        );
    }

    void updateJpaEntity(CompanyJpaEntity entity, Company company) {
        entity.update(
                company.getCorporateName(), company.getTradeName(), company.getDocument(), company.getEmail(),
                company.getPhone(), company.getAddress(), company.getLogo(), company.isActive()
        );
    }
}
