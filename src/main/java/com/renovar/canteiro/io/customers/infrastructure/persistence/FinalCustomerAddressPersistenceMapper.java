package com.renovar.canteiro.io.customers.infrastructure.persistence;

import com.renovar.canteiro.io.customers.domain.FinalCustomerAddress;
import org.springframework.stereotype.Component;

@Component
class FinalCustomerAddressPersistenceMapper {

    FinalCustomerAddressJpaEntity toJpaEntity(FinalCustomerAddress address) {
        return new FinalCustomerAddressJpaEntity(
                address.getCompanyId(), address.getFinalCustomerId(), address.getLabel(), address.getPostalCode(),
                address.getStreet(), address.getNumber(), address.getComplement(), address.getDistrict(), address.getCity(),
                address.getState(), address.getCountry(), address.isPrimaryAddress(), address.isActive()
        );
    }

    FinalCustomerAddress toDomain(FinalCustomerAddressJpaEntity entity) {
        return FinalCustomerAddress.rehydrate(
                entity.getId(), entity.getCompanyId(), entity.getFinalCustomerId(), entity.getLabel(), entity.getPostalCode(),
                entity.getStreet(), entity.getNumber(), entity.getComplement(), entity.getDistrict(), entity.getCity(),
                entity.getState(), entity.getCountry(), entity.isPrimaryAddress(), entity.isActive(), entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
