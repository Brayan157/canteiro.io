package com.renovar.canteiro.io.customers.infrastructure.persistence;

import com.renovar.canteiro.io.customers.domain.FinalCustomerContact;
import org.springframework.stereotype.Component;

@Component
class FinalCustomerContactPersistenceMapper {

    FinalCustomerContactJpaEntity toJpaEntity(FinalCustomerContact contact) {
        return new FinalCustomerContactJpaEntity(
                contact.getCompanyId(), contact.getFinalCustomerId(), contact.getName(), contact.getEmail(),
                contact.getPhone(), contact.isPrimaryContact(), contact.isActive()
        );
    }

    FinalCustomerContact toDomain(FinalCustomerContactJpaEntity entity) {
        return FinalCustomerContact.rehydrate(
                entity.getId(), entity.getCompanyId(), entity.getFinalCustomerId(), entity.getName(), entity.getEmail(),
                entity.getPhone(), entity.isPrimaryContact(), entity.isActive(), entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }
}
