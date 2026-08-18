package com.renovar.canteiro.io.customers.infrastructure.persistence;

import com.renovar.canteiro.io.customers.domain.FinalCustomer;
import org.springframework.stereotype.Component;

@Component
class FinalCustomerPersistenceMapper {

    FinalCustomerJpaEntity toJpaEntity(FinalCustomer finalCustomer) {
        return new FinalCustomerJpaEntity(
                finalCustomer.getCompanyId(),
                finalCustomer.getCustomerType(),
                finalCustomer.getName(),
                finalCustomer.getDocument(),
                finalCustomer.isActive()
        );
    }

    void updateJpaEntity(FinalCustomerJpaEntity entity, FinalCustomer finalCustomer) {
        entity.update(finalCustomer.getName(), finalCustomer.isActive());
    }

    FinalCustomer toDomain(FinalCustomerJpaEntity entity) {
        return FinalCustomer.rehydrate(
                entity.getId(),
                entity.getCompanyId(),
                entity.getCustomerType(),
                entity.getName(),
                entity.getDocument(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
